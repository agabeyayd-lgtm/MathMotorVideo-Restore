package com.mathmotor.video

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Debug-only bridge for emulator smoke tests.
 *
 * This activity is declared only by app/src/debug/AndroidManifest.xml, so it
 * is not packaged into the release APK. It lets CI pass a local test video to
 * the app's non-exported editor without exposing that editor in production.
 */
class EditorTestEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUri = intent.getStringExtra("video_uri")
        val draftId = intent.getStringExtra("draft_id")
        val timelineSyncTest = intent.getBooleanExtra("timeline_sync_test", false)
        android.util.Log.i("EditorSmokeTest", "EDITOR_SMOKE_ENTRY_STARTED uri=$videoUri")
        if (videoUri.isNullOrBlank()) {
            finish()
            return
        }

        android.util.Log.i("EditorSmokeTest", "EDITOR_SMOKE_STARTING_EDITOR")
        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            putExtra("video_uri", videoUri)
            if (!draftId.isNullOrBlank()) putExtra("draft_id", draftId)
            if (timelineSyncTest) putExtra("timeline_sync_test", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
