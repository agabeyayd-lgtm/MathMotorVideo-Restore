package com.mathmotor.video

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoProjectDraftBackupTest {

    @Test
    fun backupRestoresValidDraftsWithoutOverwritingExistingDrafts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val draftsDirectory = File(context.filesDir, "video-project-drafts")
        val idsBefore = VideoProjectDraftStore.list(context).mapTo(mutableSetOf()) { it.id }
        val collidingId = VideoProjectDraftStore.newId()

        fun project(clipTitle: String) = JSONObject().apply {
            put("format", "MathMotorVideoProject")
            put("clips", JSONArray().put(JSONObject().put("title", clipTitle)))
        }

        try {
            assertTrue(VideoProjectDraftStore.save(context, collidingId, "Telefondaki taslak", project("local clip")))

            val backup = JSONObject().apply {
                put("format", "MathMotorVideoDraftBackup")
                put("version", 1)
                put("drafts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", collidingId)
                        put("title", "Yedekten gelen taslak")
                        put("project", project("backup clip"))
                    })
                    put(JSONObject().apply {
                        put("id", "not-a-uuid")
                        put("title", "Geçersiz kayıt")
                        put("project", project("bad clip"))
                    })
                })
            }

            val result = VideoProjectDraftStore.restoreBackup(context, backup)

            assertEquals(1, result.restoredCount)
            assertEquals(1, result.skippedCount)
            val allDrafts = VideoProjectDraftStore.list(context)
            val localDraft = allDrafts.single { it.id == collidingId }
            val restoredDraft = allDrafts.single { it.title == "Yedekten gelen taslak" }
            assertEquals("Telefondaki taslak", localDraft.title)
            assertNotEquals(collidingId, restoredDraft.id)
            assertEquals(
                "backup clip",
                VideoProjectDraftStore.load(context, restoredDraft.id)
                    ?.optJSONArray("clips")?.optJSONObject(0)?.optString("title")
            )

            val exported = VideoProjectDraftStore.createBackup(context)
            assertTrue((exported.optJSONArray("drafts")?.length() ?: 0) >= 2)
        } finally {
            VideoProjectDraftStore.list(context)
                .filter { it.id !in idsBefore }
                .forEach { File(draftsDirectory, "${it.id}.json").delete() }
        }
    }

    @Test
    fun rejectsUnsupportedBackupVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backup = JSONObject().apply {
            put("format", "MathMotorVideoDraftBackup")
            put("version", 99)
            put("drafts", JSONArray().put(JSONObject()))
        }
        var rejected = false
        try {
            VideoProjectDraftStore.restoreBackup(context, backup)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Unsupported backup version must be rejected", rejected)
    }
}
