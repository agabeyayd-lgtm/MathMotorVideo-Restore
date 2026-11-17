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
    // All cells for one source used to enumerate and sort the same JPEG directory
    // independently. Build a per-source timestamp index once, then serve exact
    // timestamp variant lookups from memory while concurrent decoders run.
    private val sourceFrameIndexes =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentSkipListMap<Long, MutableSet<File>>>()

    private fun indexedFramesFor(sourceKey: String): java.util.concurrent.ConcurrentSkipListMap<Long, MutableSet<File>> =
        sourceFrameIndexes.computeIfAbsent(sourceKey) {
            val index = java.util.concurrent.ConcurrentSkipListMap<Long, MutableSet<File>>()
            sourceDirectory(sourceKey).listFiles()?.forEach { file ->
                if (!file.isFile || !file.extension.equals("jpg", ignoreCase = true)) {
                    return@forEach
                }
                val timestamp = file.name.substringBefore('_').toLongOrNull()
                    ?: return@forEach
                index.computeIfAbsent(timestamp) {
                    java.util.concurrent.ConcurrentHashMap.newKeySet<File>()
                }.add(file)
            }
            index
        }

    private fun addIndexedFrame(sourceKey: String, timestampMs: Long, file: File) {
        sourceFrameIndexes[sourceKey]?.computeIfAbsent(timestampMs.coerceAtLeast(0L)) {
            java.util.concurrent.ConcurrentHashMap.newKeySet<File>()
        }?.add(file)
    }

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
            sourceFrameIndexes[sourceKey]?.get(timestampMs.coerceAtLeast(0L))?.remove(file)
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
        val safeTimestamp = timestampMs.coerceAtLeast(0L)
        val distanceLimit = maxDistanceMs.coerceAtLeast(0L)
        val lowerTimestamp = (safeTimestamp - distanceLimit).coerceAtLeast(0L)
        val upperTimestamp = if (Long.MAX_VALUE - safeTimestamp < distanceLimit) {
            Long.MAX_VALUE
        } else {
            safeTimestamp + distanceLimit
        }
        // File enumeration is performed once per source, not once per visible
        // thumbnail. In particular, maxDistanceMs=0 (the normal exact-frame
        // fallback) becomes a direct timestamp-index lookup rather than a full
        // directory scan and sort on every worker.
        val candidates = indexedFramesFor(sourceKey)
            .subMap(lowerTimestamp, true, upperTimestamp, true)
            .entries
            .asSequence()
            .flatMap { entry ->
                entry.value.asSequence()
                    .filter { it.isFile }
                    .map { file ->
                        Triple(abs(entry.key - safeTimestamp), entry.key, file)
                    }
            }
            .sortedWith(
                compareBy<Triple<Long, Long, File>> { it.first }
                    .thenByDescending { it.third.length() }
            )
            .toList()

        for ((_, cachedTime, file) in candidates) {
            val bitmap = decode(file)
            if (bitmap != null) {
                file.setLastModified(System.currentTimeMillis())
                return CachedFrame(cachedTime, bitmap)
            }
            file.delete()
            sourceFrameIndexes[sourceKey]?.get(cachedTime)?.remove(file)
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

        val safeTimestamp = timestampMs.coerceAtLeast(0L)
        val destination = frameFile(sourceKey, safeTimestamp, widthPx, heightPx)
        if (destination.isFile && destination.length() > 0L) {
            addIndexedFrame(sourceKey, safeTimestamp, destination)
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
            addIndexedFrame(sourceKey, safeTimestamp, destination)
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
            // Eviction changes the on-disk contents; rebuild indexes lazily after
            // this rare maintenance pass rather than retaining stale file entries.
            sourceFrameIndexes.clear()
        } finally {
            trimInProgress.set(false)
        }
    }
}
