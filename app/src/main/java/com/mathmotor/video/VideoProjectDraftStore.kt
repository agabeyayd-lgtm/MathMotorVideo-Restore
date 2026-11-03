package com.mathmotor.video

import android.content.Context
import org.json.JSONArray
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

    data class RestoreResult(
        val restoredCount: Int,
        val skippedCount: Int
    )

    private const val DIRECTORY = "video-project-drafts"
    private const val FORMAT = "MathMotorVideoProject"
    private const val BACKUP_FORMAT = "MathMotorVideoDraftBackup"
    private const val BACKUP_VERSION = 1
    private const val MAX_BACKUP_DRAFTS = 250
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

    /**
     * Creates one portable JSON backup containing every valid local draft.
     * Source media files themselves are not copied; their URI references remain in each project.
     */
    fun createBackup(context: Context): JSONObject {
        val entries = JSONArray()
        list(context).forEach { draft ->
            val project = load(context, draft.id) ?: return@forEach
            entries.put(JSONObject().apply {
                put("id", draft.id)
                put("title", draft.title)
                put("savedAt", draft.savedAt)
                put("project", project)
            })
        }
        require(entries.length() > 0) { "Yedeklenecek taslak bulunamadı." }
        require(entries.length() <= MAX_BACKUP_DRAFTS) { "Tek yedekte en fazla $MAX_BACKUP_DRAFTS taslak olabilir." }
        return JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("version", BACKUP_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("drafts", entries)
        }
    }

    /**
     * Restores valid entries from a backup. Existing IDs are never overwritten:
     * collisions receive a new ID so an import cannot silently replace a local draft.
     */
    fun restoreBackup(context: Context, backup: JSONObject): RestoreResult {
        require(backup.optString("format") == BACKUP_FORMAT) { "Bu dosya MathMotor taslak yedeği değil." }
        require(backup.optInt("version", -1) == BACKUP_VERSION) { "Taslak yedeği sürümü desteklenmiyor." }
        val entries = backup.optJSONArray("drafts") ?: error("Yedekte taslak listesi bulunamadı.")
        require(entries.length() in 1..MAX_BACKUP_DRAFTS) { "Yedek boş veya izin verilen taslak sayısını aşıyor." }

        val usedIds = list(context).mapTo(mutableSetOf()) { it.id }
        var restored = 0
        var skipped = 0
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index)
            val sourceId = entry?.optString("id").orEmpty()
            val title = entry?.optString("title").orEmpty().ifBlank { "Video taslağı" }
            val project = entry?.optJSONObject("project")
            val clips = project?.optJSONArray("clips")
            if (!isValidId(sourceId) || project == null ||
                project.optString("format") != FORMAT || clips == null || clips.length() == 0
            ) {
                skipped++
                continue
            }

            val destinationId = if (usedIds.add(sourceId)) {
                sourceId
            } else {
                newId().also { usedIds.add(it) }
            }
            if (save(context, destinationId, title, project)) restored++ else skipped++
        }
        return RestoreResult(restored, skipped)
    }

    private fun draftsDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY)
}
