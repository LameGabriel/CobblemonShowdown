package com.lame.cobblemonshowdown.command

import com.lame.cobblemonshowdown.queue.BattleQueue
import com.lame.cobblemonshowdown.queue.JoinResult
import com.lame.cobblemonshowdown.queue.MatchResult
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Registers the `/showdown` command tree.
 *
 * ```
 * /showdown queue join    – Join the matchmaking queue
 * /showdown queue leave   – Leave the matchmaking queue
 * /showdown queue status  – Show current queue size and your position
 * /showdown queue         – Alias for status
 * ```
 */
object ShowdownCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("showdown")
                    .then(
                        literal("queue")
                            .then(
                                literal("join")
                                    .executes { ctx -> handleJoin(ctx.source) }
                            )
                            .then(
                                literal("leave")
                                    .executes { ctx -> handleLeave(ctx.source) }
                            )
                            .then(
                                literal("status")
                                    .executes { ctx -> handleStatus(ctx.source) }
                            )
                            // /showdown queue with no sub-command → same as status
                            .executes { ctx -> handleStatus(ctx.source) }
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

private fun handleJoin(source: ServerCommandSource): Int {
    val player = source.player ?: run {
        source.sendError(Text.translatable("cobblemonshowdown.command.player_only"))
        return 0
    }

    return when (BattleQueue.joinQueue(player)) {
        JoinResult.SUCCESS -> {
            player.sendMessage(
                Text.translatable(
                    "cobblemonshowdown.queue.joined",
                    BattleQueue.size(),
                    BattleQueue.positionOf(player)
                )
            )
            // Try to match immediately; notify both players if a battle starts.
            val match = BattleQueue.tryMatch(source.server)
            notifyMatch(match)
            1
        }

        JoinResult.ALREADY_IN_QUEUE -> {
            player.sendMessage(Text.translatable("cobblemonshowdown.queue.already_joined"))
            0
        }

        JoinResult.ALREADY_IN_BATTLE -> {
            player.sendMessage(Text.translatable("cobblemonshowdown.queue.in_battle"))
            0
        }
    }
}

private fun handleLeave(source: ServerCommandSource): Int {
    val player = source.player ?: run {
        source.sendError(Text.translatable("cobblemonshowdown.command.player_only"))
        return 0
    }

    return if (BattleQueue.leaveQueue(player)) {
        player.sendMessage(Text.translatable("cobblemonshowdown.queue.left"))
        1
    } else {
        player.sendMessage(Text.translatable("cobblemonshowdown.queue.not_in_queue"))
        0
    }
}

private fun handleStatus(source: ServerCommandSource): Int {
    val player = source.player
    val size = BattleQueue.size()

    if (player != null && BattleQueue.isInQueue(player)) {
        val pos = BattleQueue.positionOf(player)
        player.sendMessage(
            Text.translatable("cobblemonshowdown.queue.status_position", size, pos)
        )
    } else {
        val target = Text.translatable("cobblemonshowdown.queue.status", size)
        if (player != null) {
            player.sendMessage(target)
        } else {
            source.sendMessage(target)
        }
    }
    return 1
}

// ---------------------------------------------------------------------------
// Match notification helper
// ---------------------------------------------------------------------------

private fun notifyMatch(result: MatchResult?) {
    when (result) {
        is MatchResult.Success -> {
            result.player1.sendMessage(Text.translatable("cobblemonshowdown.queue.battle_found"))
            result.player2.sendMessage(Text.translatable("cobblemonshowdown.queue.battle_found"))
        }

        is MatchResult.BattleFailed -> {
            // Convert each error to a readable sentence (e.g. NO_ALIVE_POKEMON → "No alive pokemon").
            val msg = result.errors.joinToString("; ") { error ->
                error.toString()
                    .substringAfterLast('.')   // strip any class/package prefix
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .ifBlank { "unknown error" }
            }
            result.player1.sendMessage(
                Text.translatable("cobblemonshowdown.queue.battle_failed", msg)
            )
            result.player2.sendMessage(
                Text.translatable("cobblemonshowdown.queue.battle_failed", msg)
            )
        }

        is MatchResult.PlayerOffline -> {
            // One or both players were offline; the online player was already re-queued
            // inside BattleQueue.tryMatch. Nothing more to do here.
        }

        null -> { /* Not enough players yet */ }
    }
}
