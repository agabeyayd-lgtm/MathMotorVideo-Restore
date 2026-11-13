package com.mathmotor.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Small persistent cache for decoded filmstrip frames.
 *
 * Keys are derived from the source URI and source timestamp. Cache reads and
 * writes are deliberately lock-free across different frames: a disk operation
 * for one visible cell must never serialize the other two thumbnail workers.
 */
internal class TimelineThumbnailDiskCache(
    private val directory: File,
    private val maxBytes: Long = 96L * 1024L * 1024L
) {
    data class CachedFrame(val timestampMs: Long, val bitmap: Bitmap)

    // Track bytes added during this Activity lifetime. Existing cache size is
    // reconciled only when the soft threshold is reached, never during the first
    // frame write on a cold import.
    private val cachedBytes = AtomicLong(0L)
    private val trimInProgress = AtomicBoolean(false)

    fun getExact(
        sourceKey: String,
        timestampMs: Long,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        val file = frameFile(sourceKey, timestampMs, widthPx, heightPx)
        if (!file.isFile) return null
        val bitmap = decode(file)
        if (bitmap == null) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    /**
     * Returns the closest cached timestamp. Directory enumeration and bitmap
     * decoding happen without a shared monitor, so parallel readers do not wait
     * behind JPEG compression or an LRU trim from another worker.
     */
    fun findNearest(
        sourceKey: String,
        timestampMs: Long,
        maxDistanceMs: Long
    ): CachedFrame? {
        val sourceDirectory = sourceDirectory(sourceKey)
        val candidates = sourceDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            ?.mapNotNull { file ->
                val cachedTime = file.name.substringBefore('_').toLongOrNull()
                    ?: return@mapNotNull null
                val distance = abs(cachedTime - timestampMs)
                if (distance > maxDistanceMs) return@mapNotNull null
                Triple(distance, cachedTime, file)
            }
            ?.sortedWith(
                compareBy<Triple<Long, Long, File>> { it.first }
                    .thenByDescending { it.third.length() }
            )
            ?.toList()
            .orEmpty()

        for ((_, cachedTime, file) in candidates) {
            val bitmap = decode(file)
            if (bitmap != null) {
                file.setLastModified(System.currentTimeMillis())
                return CachedFrame(cachedTime, bitmap)
            }
            file.delete()
        }
        return null
    }

    fun put(
        sourceKey: String,
        timestampMs: Long,
        widthPx: Int,
        heightPx: Int,
        bitmap: Bitmap
    ) {
        if (bitmap.isRecycled || widthPx <= 0 || heightPx <= 0) return
        val sourceDirectory = sourceDirectory(sourceKey)
        if (!sourceDirectory.exists() && !sourceDirectory.mkdirs()) return

        val destination = frameFile(sourceKey, timestampMs, widthPx, heightPx)
        if (destination.isFile && destination.length() > 0L) {
            destination.setLastModified(System.currentTimeMillis())
            return
        }

        // Each writer gets its own temporary file. Publishing is one same-directory
        // rename, so readers see either no frame or the complete JPEG, never a
        // partially written image. No global lock is held during JPEG compression.
        val temporary = File(
            sourceDirectory,
            "${destination.name}.${Thread.currentThread().id}.tmp"
        )
        val written = runCatching {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    throw IllegalStateException("Thumbnail JPEG compression failed")
                }
                output.flush()
            }
            if (destination.exists()) {
                temporary.delete()
                false
            } else if (temporary.renameTo(destination)) {
                true
            } else {
                if (destination.isFile && destination.length() > 0L) {
                    temporary.delete()
                    false
                } else {
                    throw IllegalStateException("Could not atomically publish thumbnail cache file")
                }
            }
        }.getOrElse {
            temporary.delete()
            false
        }

        if (written) {
            val total = addToKnownSize(destination.length())
            // Directory walking/LRU deletion is exceptional maintenance, not part
            // of every thumbnail write. In particular it never blocks cache reads.
            // Reconcile the real on-disk size only after accumulating a meaningful
            // amount of new data. A cold import with a handful of small frames
            // performs no recursive cache-directory scan.
            if (total > maxBytes / 8L) trimToLimit()
        }
    }

    private fun addToKnownSize(bytes: Long): Long =
        cachedBytes.addAndGet(bytes.coerceAtLeast(0L))

    private fun decode(file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()?.takeUnless { it.isRecycled }

    private fun frameFile(
        sourceKey: String,
        timestampMs: Long,
        widthPx: Int,
        heightPx: Int
    ): File {
        val safeTime = timestampMs.coerceAtLeast(0L)
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)
        return File(
            sourceDirectory(sourceKey),
            "${safeTime}_${safeWidth}x${safeHeight}.jpg"
        )
    }

    private fun sourceDirectory(sourceKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory, digest)
    }

    private fun trimToLimit() {
        if (!trimInProgress.compareAndSet(false, true)) return
        try {
            val files = directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
                .toList()
            var totalBytes = files.sumOf { it.length() }
            if (totalBytes > maxBytes) {
                files.sortedBy { it.lastModified() }.forEach { file ->
                    if (totalBytes <= maxBytes) return@forEach
                    val size = file.length()
                    if (file.delete()) totalBytes -= size
                }
            }
            cachedBytes.set(0L)
            directory.walkBottomUp()
                .filter { it.isDirectory && it != directory }
                .forEach { dir -> if (dir.list().isNullOrEmpty()) dir.delete() }
        } finally {
            trimInProgress.set(false)
        }
    }
}
