package com.mathmotor.video

/**
 * Lower numeric priorities run first: visible frames, nearby look-ahead frames, then distant frames.
 * The sequence preserves FIFO ordering among requests with the same visibility priority.
 */
internal data class TimelineThumbnailWorkOrder(
    val priority: Int,
    val sequence: Long
) : Comparable<TimelineThumbnailWorkOrder> {
    override fun compareTo(other: TimelineThumbnailWorkOrder): Int {
        val byPriority = priority.compareTo(other.priority)
        return if (byPriority != 0) byPriority else sequence.compareTo(other.sequence)
    }
}

internal class TimelineThumbnailDecodeTask(
    private val order: TimelineThumbnailWorkOrder,
    private val decode: () -> Unit
) : Runnable, Comparable<TimelineThumbnailDecodeTask> {
    override fun compareTo(other: TimelineThumbnailDecodeTask): Int = order.compareTo(other.order)
    override fun run() = decode()
}
