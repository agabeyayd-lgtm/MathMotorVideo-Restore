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
        if (videoUri.isNullOrBlank()) {
            finish()
            return
        }

        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            putExtra("video_uri", videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
