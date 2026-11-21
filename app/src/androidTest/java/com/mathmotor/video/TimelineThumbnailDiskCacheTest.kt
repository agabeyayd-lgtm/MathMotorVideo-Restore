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
}
