package com.mathmotor.video

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
}
