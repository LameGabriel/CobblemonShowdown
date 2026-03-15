package com.lame.cobblemonshowdown.queue

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleStartResult
import com.lame.cobblemonshowdown.CobblemonShowdown
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Result returned when two players are matched from the queue.
 */
sealed class MatchResult {
    /** Both players were matched and a battle was successfully started. */
    data class Success(val player1: ServerPlayerEntity, val player2: ServerPlayerEntity) : MatchResult()

    /** The players were matched but the battle could not start (e.g. no Pokémon). */
    data class BattleFailed(
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        val errors: List<String>
    ) : MatchResult()

    /** One or both players were offline when the match was attempted; nothing was started. */
    data class PlayerOffline(val player1Id: UUID, val player2Id: UUID) : MatchResult()
}

/**
 * Reason a join attempt was rejected.
 */
enum class JoinResult { SUCCESS, ALREADY_IN_QUEUE, ALREADY_IN_BATTLE }

/**
 * Thread-affine (Minecraft server-thread only) matchmaking queue.
 *
 * All public methods **must** be called from the server thread.  This matches the
 * normal Fabric event / command execution context so no extra synchronisation is
 * needed.
 */
object BattleQueue {

    // LinkedHashMap preserves insertion order so older entries are matched first (FIFO).
    private val entries: LinkedHashMap<UUID, QueueEntry> = LinkedHashMap()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Add [player] to the queue.
     * Returns [JoinResult.SUCCESS] on success, or the specific reason for rejection.
     */
    fun joinQueue(player: ServerPlayerEntity): JoinResult {
        if (entries.containsKey(player.uuid)) return JoinResult.ALREADY_IN_QUEUE
        if (isPlayerInBattle(player)) return JoinResult.ALREADY_IN_BATTLE

        entries[player.uuid] = QueueEntry(player.uuid)
        CobblemonShowdown.LOGGER.info("${player.name.string} joined the queue (size=${entries.size})")
        return JoinResult.SUCCESS
    }

    /**
     * Remove [player] from the queue.
     * Returns `true` if they were in the queue, `false` otherwise.
     */
    fun leaveQueue(player: ServerPlayerEntity): Boolean = leaveQueue(player.uuid)

    /** Remove a player by UUID (used on disconnect). */
    fun leaveQueue(playerId: UUID): Boolean {
        val removed = entries.remove(playerId) != null
        if (removed) CobblemonShowdown.LOGGER.info("$playerId left the queue (size=${entries.size})")
        return removed
    }

    /** Returns `true` if [player] is currently in the queue. */
    fun isInQueue(player: ServerPlayerEntity): Boolean = entries.containsKey(player.uuid)

    /** 1-based queue position of [player], or -1 if not in queue. */
    fun positionOf(player: ServerPlayerEntity): Int {
        var pos = 0
        for (key in entries.keys) {
            pos++
            if (key == player.uuid) return pos
        }
        return -1
    }

    /** Number of players currently waiting. */
    fun size(): Int = entries.size

    /**
     * Remove any entries whose players are no longer online.
     * Call this periodically (e.g. every server tick) to keep the queue clean.
     */
    fun removeOfflinePlayers(server: MinecraftServer) {
        val offline = entries.keys.filter { uuid -> server.playerManager.getPlayer(uuid) == null }
        offline.forEach { uuid ->
            entries.remove(uuid)
            CobblemonShowdown.LOGGER.info("Removed offline player $uuid from queue")
        }
    }

    /**
     * Attempt to match the two oldest players in the queue and start a battle.
     *
     * Call this after every [joinQueue] and also on a periodic tick so stale
     * entries never block matches.
     *
     * @return A [MatchResult] describing what happened, or `null` if fewer than
     *         two players are queued.
     */
    fun tryMatch(server: MinecraftServer): MatchResult? {
        if (entries.size < 2) return null

        val iter = entries.entries.iterator()
        val (id1, entry1) = iter.next()
        val (id2, entry2) = iter.next()

        // Always remove both candidates before doing anything else so they are
        // not re-matched on a concurrent call (even though all calls happen on
        // the server thread, this is defensive).
        entries.remove(id1)
        entries.remove(id2)

        val p1 = server.playerManager.getPlayer(id1)
        val p2 = server.playerManager.getPlayer(id2)

        if (p1 == null || p2 == null) {
            // Re-queue any still-online player, preserving the original entry so
            // their joinTime (and therefore FIFO position) is not reset.
            if (p1 != null && !isPlayerInBattle(p1)) entries[id1] = entry1
            if (p2 != null && !isPlayerInBattle(p2)) entries[id2] = entry2
            return MatchResult.PlayerOffline(id1, id2)
        }

        return startBattle(p1, p2)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun startBattle(p1: ServerPlayerEntity, p2: ServerPlayerEntity): MatchResult {
        CobblemonShowdown.LOGGER.info(
            "Attempting battle: ${p1.name.string} vs ${p2.name.string}"
        )

        val result = BattleBuilder.pvp(p1, p2, BattleFormat.GEN_9_SINGLES)

        return if (result is BattleStartResult.Error) {
            val messages = result.errors.map { it.toString() }
            CobblemonShowdown.LOGGER.warn("Battle failed: $messages")
            MatchResult.BattleFailed(p1, p2, messages)
        } else {
            CobblemonShowdown.LOGGER.info("Battle started: ${p1.name.string} vs ${p2.name.string}")
            MatchResult.Success(p1, p2)
        }
    }

    private fun isPlayerInBattle(player: ServerPlayerEntity): Boolean =
        Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null
}
