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
        val initialPhotoUri = intent.getStringExtra("initial_photo_uri")
        val overlayUri = intent.getStringExtra("overlay_uri")
        val replacementVideoUri = intent.getStringExtra("replacement_video_uri")
        val testMusicUri = intent.getStringExtra("test_music_uri")
        val testSoundUri = intent.getStringExtra("test_sound_uri")
        val draftId = intent.getStringExtra("draft_id")
        val timelineSyncTest = intent.getBooleanExtra("timeline_sync_test", false)
        val timelineTestScaleDp = intent.getFloatExtra("timeline_test_px_per_sec_dp", 0f)
        android.util.Log.i(
            "EditorSmokeTest",
            "EDITOR_SMOKE_ENTRY_STARTED uri=" + videoUri +
                " initialPhoto=" + !initialPhotoUri.isNullOrBlank()
        )
        if (videoUri.isNullOrBlank() && initialPhotoUri.isNullOrBlank()) {
            finish()
            return
        }

        android.util.Log.i("EditorSmokeTest", "EDITOR_SMOKE_STARTING_EDITOR")
        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            if (!videoUri.isNullOrBlank()) putExtra("video_uri", videoUri)
            if (!initialPhotoUri.isNullOrBlank()) putExtra("initial_overlay_uri", initialPhotoUri)
            if (!overlayUri.isNullOrBlank()) putExtra("overlay_uri", overlayUri)
            if (!replacementVideoUri.isNullOrBlank()) {
                putExtra("replacement_video_uri", replacementVideoUri)
            }
            if (!testMusicUri.isNullOrBlank()) putExtra("test_music_uri", testMusicUri)
            if (!testSoundUri.isNullOrBlank()) putExtra("test_sound_uri", testSoundUri)
            if (!draftId.isNullOrBlank()) putExtra("draft_id", draftId)
            if (timelineSyncTest) putExtra("timeline_sync_test", true)
            if (timelineTestScaleDp > 0f) putExtra("timeline_test_px_per_sec_dp", timelineTestScaleDp)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
