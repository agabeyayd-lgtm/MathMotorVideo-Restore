package com.mathmotor.video

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real persistent thumbnail cache against app-private storage.
 * A new cache instance represents a newly-created editor/process cache owner.
 */
@RunWith(AndroidJUnit4::class)
class TimelineThumbnailDiskCacheTest {

    @Test
    fun exactFrameIsReadableFromDiskByFreshCacheInstance() {
        val directory = freshDirectory("instance-reopen")
        val sourceKey = "content://mathmotor.test/video/persistent-sample"
        val expectedColor = Color.rgb(226, 44, 91)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(expectedColor)
        }

        try {
            val firstOwner = TimelineThumbnailDiskCache(directory)
            assertTrue(firstOwner.put(sourceKey, 1_713L, 128, 128, bitmap))
        } finally {
            bitmap.recycle()
        }

        // Do not reuse the first cache instance or its in-memory timestamp index.
        val reopenedOwner = TimelineThumbnailDiskCache(directory)
        val restored = reopenedOwner.getExact(sourceKey, 1_713L, 128, 128)
        assertNotNull("The new cache owner did not find the persisted JPEG", restored)
        try {
            val actual = restored!!.getPixel(64, 64)
            assertTrue("Red channel changed too much: $actual", abs(Color.red(actual) - Color.red(expectedColor)) < 24)
            assertTrue("Green channel changed too much: $actual", abs(Color.green(actual) - Color.green(expectedColor)) < 24)
            assertTrue("Blue channel changed too much: $actual", abs(Color.blue(actual) - Color.blue(expectedColor)) < 24)
        } finally {
            restored?.recycle()
            directory.deleteRecursively()
        }
    }

    @Test
    fun firstWriteReconcilesOldFilesEnforcesLimitAndRemovesAbandonedTemps() {
        val directory = freshDirectory("size-reconcile")
        val oldSource = "content://mathmotor.test/video/old"
        val originalOwner = TimelineThumbnailDiskCache(directory, 4L * 1024L * 1024L)

        repeat(10) { index ->
            val bitmap = patternedBitmap(index)
            try {
                assertTrue(originalOwner.put(oldSource, index * 1_000L, 128, 128, bitmap))
            } finally {
                bitmap.recycle()
            }
        }

        val oldJpegs = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .toList()
        val existingBytes = oldJpegs.sumOf { it.length() }
        assertTrue("Fixture cache should contain multiple kilobytes: $existingBytes", existingBytes > 8_192L)
        oldJpegs.forEach { file ->
            assertTrue("Could not mark seeded cache entry as old", file.setLastModified(1L))
        }

        val abandonedTemp = File(directory, "interrupted-write/12345_128x128.jpg.7.tmp")
        assertTrue(abandonedTemp.parentFile!!.mkdirs())
        abandonedTemp.writeBytes(ByteArray(256) { it.toByte() })
        assertTrue(abandonedTemp.setLastModified(1L))

        // This new owner sees the old files for the first time and must count
        // them before adding another frame and deciding whether eviction is needed.
        val byteLimit = existingBytes / 2L
        val newOwner = TimelineThumbnailDiskCache(directory, byteLimit)
        val newestSource = "content://mathmotor.test/video/new"
        val newestBitmap = patternedBitmap(99)
        try {
            assertTrue(newOwner.put(newestSource, 2_500L, 128, 128, newestBitmap))
        } finally {
            newestBitmap.recycle()
        }

        val remainingJpegs = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .toList()
        val remainingBytes = remainingJpegs.sumOf { it.length() }
        assertTrue("Disk cache exceeded its $byteLimit byte cap: $remainingBytes", remainingBytes <= byteLimit)
        assertFalse("A stale interrupted-write temp file was not removed", abandonedTemp.exists())

        val newestFrame = newOwner.getExact(newestSource, 2_500L, 128, 128)
        assertNotNull("LRU trim evicted the newly-written frame", newestFrame)
        newestFrame?.recycle()
        directory.deleteRecursively()
    }

    private fun freshDirectory(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "thumbnail-cache-tests/$name").apply {
            deleteRecursively()
            check(mkdirs() || isDirectory) { "Could not create cache test directory: $absolutePath" }
        }
    }

    private fun patternedBitmap(seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val value = (x * 31 + y * 17 + seed * 13) and 0xff
                bitmap.setPixel(
                    x,
                    y,
                    Color.rgb(value, (value * 7 + seed) and 0xff, (value * 13 + 29) and 0xff)
                )
            }
        }
        return bitmap
    }

    @Test
    fun coarseGridPersistsAcrossCacheOwnersAndKeepsSourcesIsolated() {
        val directory = freshDirectory("coarse-grid-reopen")
        val sourceA = "content://mathmotor.test/video/grid-a|duration=5000"
        val sourceB = "content://mathmotor.test/video/grid-b|duration=5000"
        val colors = listOf(
            Color.rgb(220, 40, 40),
            Color.rgb(40, 210, 70),
            Color.rgb(40, 80, 220)
        )
        try {
            val firstOwner = TimelineThumbnailDiskCache(directory)
            listOf(0L, 500L, 1_000L).forEachIndexed { index, timestamp ->
                val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(colors[index])
                }
                try {
                    assertTrue(firstOwner.put(sourceA, timestamp, 48, 48, bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
            assertTrue(firstOwner.markGridComplete(sourceA))
            val other = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.MAGENTA)
            }
            try {
                assertTrue(firstOwner.put(sourceB, 500L, 48, 48, other))
                assertTrue(firstOwner.markGridComplete(sourceB))
            } finally {
                other.recycle()
            }

            val reopenedOwner = TimelineThumbnailDiskCache(directory)
            assertTrue(reopenedOwner.isGridComplete(sourceA))
            assertTrue(reopenedOwner.isGridComplete(sourceB))
            val nearest = reopenedOwner.findNearest(sourceA, 740L, Long.MAX_VALUE)
            assertNotNull(nearest)
            try {
                assertEquals(500L, nearest!!.timestampMs)
                assertTrue(abs(Color.green(nearest.bitmap.getPixel(24, 24)) - 210) < 25)
            } finally {
                nearest?.bitmap?.recycle()
            }
            val isolated = reopenedOwner.findNearest(sourceB, 500L, Long.MAX_VALUE)
            assertNotNull(isolated)
            try {
                val pixel = isolated!!.bitmap.getPixel(24, 24)
                assertTrue(abs(Color.red(pixel) - 255) < 25)
                assertTrue(abs(Color.green(pixel)) < 25)
                assertTrue(abs(Color.blue(pixel) - 255) < 25)
            } finally {
                isolated?.bitmap?.recycle()
            }
            reopenedOwner.deleteSource(sourceA)
            assertFalse(reopenedOwner.isGridComplete(sourceA))
            assertTrue(reopenedOwner.isGridComplete(sourceB))
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test
    fun lruEvictionInvalidatesCompletedGridMarker() {
        val directory = freshDirectory("grid-marker-eviction")
        val source = "content://mathmotor.test/video/grid-eviction"
        val cache = TimelineThumbnailDiskCache(directory, 96L * 1024L * 1024L, 1)
        val first = patternedBitmap(701)
        try {
            assertTrue(cache.put(source, 0L, 48, 48, first))
        } finally {
            first.recycle()
        }
        assertTrue(cache.markGridComplete(source))
        val firstJpeg = directory.walkTopDown()
            .first { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
        assertTrue("Could not age the first grid frame", firstJpeg.setLastModified(1L))

        val second = patternedBitmap(702)
        try {
            assertTrue(cache.put(source, 500L, 48, 48, second))
        } finally {
            second.recycle()
        }

        assertFalse(
            "The complete marker survived eviction of one of its grid frames",
            cache.isGridComplete(source)
        )
        assertEquals(
            1,
            directory.walkTopDown()
                .count { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
        )
        directory.deleteRecursively()
    }

    @Test
    fun cacheEnforcesFileCountCapEvenWhenByteBudgetIsNotReached() {
        val directory = freshDirectory("file-count-cap")
        val cache = TimelineThumbnailDiskCache(directory, 96L * 1024L * 1024L, 3)
        val source = "content://mathmotor.test/video/file-count"
        repeat(8) { index ->
            val bitmap = patternedBitmap(index + 300)
            try {
                assertTrue(cache.put(source, index * 500L, 48, 48, bitmap))
            } finally {
                bitmap.recycle()
            }
        }
        val files = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .count()
        assertEquals(3, files)
        directory.deleteRecursively()
    }

}
