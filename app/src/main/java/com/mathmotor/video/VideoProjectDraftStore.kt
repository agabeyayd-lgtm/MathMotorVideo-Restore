package com.mathmotor.video

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local, private draft storage for the editor.
 * The saved payload reuses the project's JSON format so all editable layers/settings
 * are restored by the same routine used by the manual project-open action.
 */
object VideoProjectDraftStore {
    data class Draft(
        val id: String,
        val title: String,
        val savedAt: Long,
        val clipCount: Int
    )

    private const val DIRECTORY = "video-project-drafts"
    private const val FORMAT = "MathMotorVideoProject"
    private const val ID_KEY = "_draftId"
    private const val TITLE_KEY = "_draftTitle"
    private const val SAVED_AT_KEY = "_draftSavedAt"

    fun newId(): String = UUID.randomUUID().toString()

    fun isValidId(id: String): Boolean =
        runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)

    fun save(context: Context, id: String, title: String, project: JSONObject): Boolean {
        if (!isValidId(id) || project.optString("format") != FORMAT) return false
        val clips = project.optJSONArray("clips") ?: return false
        if (clips.length() == 0) return false

        val directory = draftsDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) return false
        val now = System.currentTimeMillis()
        val payload = JSONObject(project.toString()).apply {
            put(ID_KEY, id)
            put(TITLE_KEY, title.trim().take(100).ifBlank { "Video taslağı" })
            put(SAVED_AT_KEY, now)
        }
        val destination = File(directory, "$id.json")
        val temporary = File(directory, "$id.json.tmp")
        return try {
            temporary.writeText(payload.toString(), Charsets.UTF_8)
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                return false
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.isFile && destination.length() > 0
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    fun load(context: Context, id: String): JSONObject? {
        if (!isValidId(id)) return null
        val file = File(draftsDirectory(context), "$id.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val clips = json.optJSONArray("clips")
            require(json.optString("format") == FORMAT && clips != null && clips.length() > 0)
            json
        }.getOrNull()
    }

    fun list(context: Context): List<Draft> {
        val directory = draftsDirectory(context)
        val files = directory.listFiles()?.filter {
            it.isFile && it.name.endsWith(".json") && isValidId(it.name.removeSuffix(".json"))
        } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val id = file.name.removeSuffix(".json")
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val clips = json.optJSONArray("clips") ?: return@runCatching null
                if (json.optString("format") != FORMAT || clips.length() == 0) return@runCatching null
                val firstTitle = clips.optJSONObject(0)?.optString("title").orEmpty()
                val title = json.optString(TITLE_KEY).ifBlank {
                    json.optString("exportName").takeIf { it.isNotBlank() && it != "MathMotor_Edit" }
                        ?: firstTitle.ifBlank { "Video taslağı" }
                }
                Draft(
                    id = id,
                    title = title,
                    savedAt = json.optLong(SAVED_AT_KEY, file.lastModified()),
                    clipCount = clips.length()
                )
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    private fun draftsDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY)
}
