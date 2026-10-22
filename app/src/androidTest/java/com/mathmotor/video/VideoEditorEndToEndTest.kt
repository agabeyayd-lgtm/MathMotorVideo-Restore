package com.mathmotor.video

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoEditorEndToEndTest {
    @Test
    fun mp4LoadsSplitsAndExportsPlayableMp4() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "editor-e2e-source.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("editor-e2e-source.mp4").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }

        val intent = Intent(context, VideoEditorActivity::class.java).apply {
            putExtra("video_uri", Uri.fromFile(source).toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val scenario = ActivityScenario.launch<VideoEditorActivity>(intent)
        try {
            awaitUi(scenario, 15_000L, "the source video is added") { activity ->
                allText(activity).any { it.contains("eklendi", ignoreCase = true) }
            }

            awaitUi(scenario, 10_000L, "the source video reaches a decoded playback state") { activity ->
                val playerView = find(activity.findViewById(android.R.id.content), PlayerView::class.java)
                val player = playerView?.player ?: return@awaitUi false
                player.videoSize.width > 0 &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)
            }

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val playerView = find(root, PlayerView::class.java)
                assertNotNull("Editor should contain a Media3 preview player", playerView)
                playerView?.player?.pause()
                playerView?.player?.seekTo(800L)
            }
            SystemClock.sleep(350L)

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val splitButton = findText(root, "✂ BÖL • Oynatma noktasında")
                assertNotNull("The split-at-playhead control should exist", splitButton)
                splitButton?.performClick()
            }
            awaitUi(scenario, 5_000L, "the clip is split at the playhead") { activity ->
                allText(activity).any { it.contains("iki parçaya bölündü", ignoreCase = true) }
            }

            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val saveTab = findText(root, "Kaydet")
                assertNotNull("The export tab should exist", saveTab)
                saveTab?.performClick()
                val exportButton = findText(root, "MP4 OLARAK DIŞA AKTAR")
                assertNotNull("The MP4 export control should exist", exportButton)
                exportButton?.performClick()
            }

            awaitUi(scenario, 60_000L, "MP4 export to finish") { activity ->
                allText(activity).any {
                    it.contains("BAŞARILI • Düzenlenmiş MP4 kaydedildi", ignoreCase = true) ||
                        it.contains("DIŞA AKTARMA BAŞARISIZ", ignoreCase = true) ||
                        it.contains("Dışa aktarıcı başlatılamadı", ignoreCase = true) ||
                        it.contains("Video işlendi ancak kaydedilemedi", ignoreCase = true) ||
                        it.contains("Düzenleme hazırlanamadı", ignoreCase = true)
                }
            }

            var statusText = ""
            scenario.onActivity { activity ->
                statusText = allText(activity).firstOrNull {
                    it.contains("Düzenlenmiş MP4 kaydedildi", ignoreCase = true) ||
                        it.contains("DIŞA AKTARMA BAŞARISIZ", ignoreCase = true) ||
                        it.contains("Dışa aktarıcı başlatılamadı", ignoreCase = true) ||
                        it.contains("Video işlendi ancak kaydedilemedi", ignoreCase = true) ||
                        it.contains("Düzenleme hazırlanamadı", ignoreCase = true)
                }.orEmpty()
            }
            assertTrue("MP4 export did not succeed. Editor status: $statusText",
                statusText.contains("Düzenlenmiş MP4 kaydedildi", ignoreCase = true))

            val exportedUri = Regex("content://[^\\s]+").find(statusText)?.value
                ?: throw AssertionError("Successful export did not report a MediaStore URI: $statusText")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(exportedUri))
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                assertTrue("Exported MP4 should have a readable, positive duration", duration > 0L)
                assertTrue("Exported MP4 should have valid video dimensions", width > 0 && height > 0)
            } finally {
                retriever.release()
            }
        } finally {
            scenario.close()
            source.delete()
        }
    }

    private fun awaitUi(
        scenario: ActivityScenario<VideoEditorActivity>,
        timeoutMs: Long,
        description: String,
        predicate: (VideoEditorActivity) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> matched = predicate(activity) }
            if (matched) return
            SystemClock.sleep(100L)
        }
        var state = ""
        scenario.onActivity { activity -> state = allText(activity).joinToString(" | ") }
        assertTrue("Timed out waiting for $description. Editor UI: $state", matched)
    }

    private fun allText(activity: VideoEditorActivity): List<String> =
        allTextFrom(activity.findViewById(android.R.id.content))

    private fun allTextFrom(view: View): List<String> {
        val values = mutableListOf<String>()
        if (view is TextView) values += view.text?.toString().orEmpty()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) values += allTextFrom(view.getChildAt(index))
        }
        return values
    }

    private fun findText(view: View, expected: String): TextView? {
        if (view is TextView && view.text?.toString() == expected) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findText(view.getChildAt(index), expected)
                if (found != null) return found
            }
        }
        return null
    }

    private fun <T : View> find(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = find(view.getChildAt(index), type)
                if (found != null) return found
            }
        }
        return null
    }

}
