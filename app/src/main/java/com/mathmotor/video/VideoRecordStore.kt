package com.mathmotor.video

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoRecord(val title: String, val uri: String, val seed: Long, val createdAt: Long)

object VideoRecordStore {
    private const val PREFS = "mathmotor_video_records"
    private const val KEY = "records"

    fun list(context: Context): List<VideoRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                VideoRecord(o.optString("title"), o.optString("uri"), o.optLong("seed"), o.optLong("createdAt"))
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, record: VideoRecord) {
        val all = (list(context).filterNot { it.uri == record.uri } + record)
            .sortedByDescending { it.createdAt }.take(100)
        val array = JSONArray()
        all.forEach { array.put(JSONObject().apply {
            put("title", it.title); put("uri", it.uri); put("seed", it.seed); put("createdAt", it.createdAt)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    /**
     * Rebuild the local list from exported MathMotorVideo files.
     * This is used by the one-time migration build because SharedPreferences
     * belong to the old Android application ID and cannot be shared directly.
     */
    fun importPublicMovies(context: Context): Int {
        val existing = list(context).mapTo(mutableSetOf()) { it.uri }
        var imported = 0
        val namePattern = Regex("MathMotor_(\\d+)_\\d+\\.mp4", RegexOption.IGNORE_CASE)

        fun addIfNew(name: String, uri: String, timestamp: Long) {
            if (uri in existing) return
            val seed = namePattern.find(name)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            add(context, VideoRecord(name.substringBeforeLast('.'), uri, seed, timestamp))
            existing.add(uri)
            imported++
        }

        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.IS_PENDING} = 0"
            val args = arrayOf("${Environment.DIRECTORY_MOVIES}/MathMotorVideo/")
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "MathMotor video"
                    val timestamp = cursor.getLong(dateCol).let { if (it > 0) it * 1000L else System.currentTimeMillis() }
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    addIfNew(name, uri, timestamp)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "MathMotorVideo"
            )
            folder.listFiles()?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file -> addIfNew(file.name, Uri.fromFile(file).toString(), file.lastModified()) }
        }
        return imported
    }
}
