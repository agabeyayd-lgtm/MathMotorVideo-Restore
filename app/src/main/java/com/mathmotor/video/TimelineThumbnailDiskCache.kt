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

    // The existing disk size is initialized once per cache owner. The scan is
    // performed by the dedicated writer thread, not by a thumbnail decode worker.
    // -1 means the existing on-disk size has not yet been reconciled. The first
    // reconciliation happens on the dedicated background cache-writer thread.
    private val cachedBytes = AtomicLong(-1L)
    private val sizeInitializationLock = Any()
    private val trimInProgress = AtomicBoolean(false)
    private val staleTemporaryFileAgeMs = 60L * 60L * 1_000L
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
    ): Boolean {
        if (bitmap.isRecycled || widthPx <= 0 || heightPx <= 0) return false
        val sourceDirectory = sourceDirectory(sourceKey)
        if (!sourceDirectory.exists() && !sourceDirectory.mkdirs()) return false

        val safeTimestamp = timestampMs.coerceAtLeast(0L)
        val destination = frameFile(sourceKey, safeTimestamp, widthPx, heightPx)
        if (destination.isFile && destination.length() > 0L) {
            addIndexedFrame(sourceKey, safeTimestamp, destination)
            destination.setLastModified(System.currentTimeMillis())
            return true
        }
        // A zero-byte/incomplete destination must not block a replacement write.
        if (destination.exists()) destination.delete()

        // Reconcile old cache files before publishing this new one. This runs
        // exclusively on the background disk writer, never on a decode worker.
        initializeKnownSize()

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
            // Enforce the configured cap against both pre-existing files and the
            // frame just published. This scan/eviction is isolated to the disk writer.
            if (total > maxBytes) trimToLimit()
        }
        return destination.isFile && destination.length() > 0L
    }

    private fun initializeKnownSize(): Long {
        val known = cachedBytes.get()
        if (known >= 0L) return known
        synchronized(sizeInitializationLock) {
            val secondRead = cachedBytes.get()
            if (secondRead >= 0L) return secondRead
            val cutoff = System.currentTimeMillis() - staleTemporaryFileAgeMs
            var existingBytes = 0L
            if (directory.isDirectory) {
                directory.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    if (file.name.endsWith(".tmp", ignoreCase = true)) {
                        if (file.lastModified() <= cutoff) file.delete()
                    } else if (file.extension.equals("jpg", ignoreCase = true)) {
                        existingBytes += file.length().coerceAtLeast(0L)
                    }
                }
            }
            cachedBytes.set(existingBytes)
            return existingBytes
        }
    }

    private fun addToKnownSize(bytes: Long): Long {
        initializeKnownSize()
        return cachedBytes.addAndGet(bytes.coerceAtLeast(0L))
    }

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
            // Remove abandoned temporary writes left by a process killed during
            // JPEG compression. Fresh temp files are left alone in case another
            // editor instance is still publishing one.
            val staleTempCutoff = System.currentTimeMillis() - staleTemporaryFileAgeMs
            directory.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".tmp", ignoreCase = true) }
                .filter { it.lastModified() <= staleTempCutoff }
                .forEach { it.delete() }
            cachedBytes.set(totalBytes)
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
