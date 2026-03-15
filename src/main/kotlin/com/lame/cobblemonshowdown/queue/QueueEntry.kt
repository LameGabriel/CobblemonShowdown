package com.lame.cobblemonshowdown.queue

import java.util.UUID

/**
 * A single entry in the matchmaking queue.
 *
 * @param playerId  UUID of the queued player.
 * @param joinTime  System millisecond timestamp when the player joined (used for FIFO ordering).
 */
data class QueueEntry(
    val playerId: UUID,
    val joinTime: Long = System.currentTimeMillis()
)
