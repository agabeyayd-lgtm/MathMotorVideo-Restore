package com.mathmotor.video

import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailWorkOrderTest {
    @Test
    fun visibleFramesAreOrderedBeforeNearbyAndDistantFrames() {
        val visible = TimelineThumbnailWorkOrder(priority = 0, sequence = 20)
        val nearby = TimelineThumbnailWorkOrder(priority = 1, sequence = 1)
        val distant = TimelineThumbnailWorkOrder(priority = 2, sequence = 0)

        assertTrue(visible < nearby)
        assertTrue(nearby < distant)
    }

    @Test
    fun requestsAtTheSamePriorityRemainFifo() {
        val earlier = TimelineThumbnailWorkOrder(priority = 1, sequence = 4)
        val later = TimelineThumbnailWorkOrder(priority = 1, sequence = 5)

        assertTrue(earlier < later)
    }
}
