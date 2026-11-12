package com.mathmotor.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Small persistent cache for decoded filmstrip frames.
 *
 * Keys are derived from the source URI and the source timestamp.  Keeping each
 * source in its own hashed directory avoids leaking content URIs into filenames
 * and makes the cache reusable when an editor Activity is recreated.
 */
internal class TimelineThumbnailDiskCache(
    private val directory: File,
    private val maxBytes: Long = 96L * 1024L * 1024L
) {
    data class CachedFrame(val timestampMs: Long, val bitmap: Bitmap)

    private val lock = Any()

    fun getExact(
        sourceKey: String,
        timestampMs: Long,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? = synchronized(lock) {
        val file = frameFile(sourceKey, timestampMs, widthPx, heightPx)
        if (!file.isFile) return@synchronized null
        val bitmap = decode(file)
        if (bitmap == null) {
            file.delete()
            null
        } else {
            file.setLastModified(System.currentTimeMillis())
            bitmap
        }
    }

    /**
     * Returns the closest previously decoded timestamp for this source.
     * A nearby frame may be displayed immediately while the exact timestamp is
     * decoded; an exact timestamp at another size is reusable without decoding.
     */
    fun findNearest(
        sourceKey: String,
        timestampMs: Long,
        maxDistanceMs: Long
    ): CachedFrame? = synchronized(lock) {
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
                return@synchronized CachedFrame(cachedTime, bitmap)
            }
            file.delete()
        }
        null
    }

    fun put(
        sourceKey: String,
        timestampMs: Long,
        widthPx: Int,
        heightPx: Int,
        bitmap: Bitmap
    ) {
        if (bitmap.isRecycled || widthPx <= 0 || heightPx <= 0) return
        synchronized(lock) {
            val sourceDirectory = sourceDirectory(sourceKey)
            if (!sourceDirectory.exists() && !sourceDirectory.mkdirs()) return
            val destination = frameFile(sourceKey, timestampMs, widthPx, heightPx)
            if (destination.isFile && destination.length() > 0L) {
                destination.setLastModified(System.currentTimeMillis())
                return
            }
            val temporary = File(sourceDirectory, destination.name + ".tmp")
            val written = runCatching {
                FileOutputStream(temporary).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                        throw IllegalStateException("Thumbnail JPEG compression failed")
                    }
                    output.flush()
                }
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Could not atomically publish thumbnail cache file")
                }
                true
            }.getOrDefault(false)
            if (!written) temporary.delete()
            if (written) trimToLimit()
        }
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
        val files = directory.walkTopDown().filter { it.isFile }.toList()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= maxBytes) return
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
        directory.walkBottomUp()
            .filter { it.isDirectory && it != directory }
            .forEach { dir -> if (dir.list().isNullOrEmpty()) dir.delete() }
    }
}
