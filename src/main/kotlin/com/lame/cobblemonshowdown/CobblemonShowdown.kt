package com.lame.cobblemonshowdown

import com.lame.cobblemonshowdown.command.ShowdownCommand
import com.lame.cobblemonshowdown.queue.BattleQueue
import com.lame.cobblemonshowdown.queue.MatchResult
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Main mod entry-point.  Fabric loads this via `fabric.mod.json` using the
 * `kotlin` adapter from `fabric-language-kotlin`.
 */
object CobblemonShowdown : ModInitializer {

    const val MOD_ID = "cobblemonshowdown"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    /** How often (in server ticks) to run queue maintenance (offline cleanup + match attempt). */
    private const val QUEUE_TICK_INTERVAL = 20 // once per second

    private var tickCounter = 0

    override fun onInitialize() {
        LOGGER.info("Cobblemon Showdown initialising…")

        // Register /showdown commands.
        ShowdownCommand.register()

        // Remove players from the queue when they disconnect.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            BattleQueue.leaveQueue(handler.player.uuid)
        }

        // Periodically clean up offline players and attempt new matches.
        // This runs every QUEUE_TICK_INTERVAL server ticks (default: 1 second).
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            if (tickCounter >= QUEUE_TICK_INTERVAL) {
                tickCounter = 0
                BattleQueue.removeOfflinePlayers(server)

                // Only continue matching when a battle was successfully started.
                // PlayerOffline / BattleFailed results break the loop to avoid
                // spinning; the next tick will handle remaining players.
                while (BattleQueue.size() >= 2) {
                    if (BattleQueue.tryMatch(server) !is MatchResult.Success) break
                }
            }
        }

        LOGGER.info("Cobblemon Showdown ready!  Use /showdown queue join to start matchmaking.")
    }
}
