package com.mathmotor.video

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.widget.Toast

class VideoPlayerActivity : Activity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private lateinit var info: TextView
    private lateinit var historyContainer: LinearLayout
    private var activeUri: Uri? = null
    private val pickVideoRequest = 4107

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 31, 45))
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "VİDEO İZLEME"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Oluşturduğun videoları izle veya telefondan bir video aç."
            textSize = 13f
            setTextColor(Color.rgb(173, 191, 204))
            setPadding(0, dp(5), 0, dp(12))
        })
        playerView = PlayerView(this).apply { setBackgroundColor(Color.BLACK); useController = true }
        root.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        info = TextView(this).apply {
            text = "Bir video seç veya aşağıdaki geçmişten bir kayda dokun."
            textSize = 13f
            setTextColor(Color.rgb(225, 233, 237))
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(info)
        root.addView(actionButton("TELEFONDAN VİDEO SEÇ") { chooseVideo() })
        root.addView(actionButton("OYNAT / DEVAM ET") {
            if (activeUri == null) {
                val first = VideoRecordStore.list(this).firstOrNull()
                if (first != null) play(Uri.parse(first.uri), first.title) else chooseVideo()
            } else player?.play()
        })
        root.addView(actionButton("DURAKLAT") { player?.pause() })
        root.addView(actionButton("GERİ DÖN") { finish() })
        root.addView(TextView(this).apply {
            text = "VİDEO GEÇMİŞİ"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(121, 202, 218))
            setPadding(0, dp(16), 0, dp(8))
        })
        val historyScroll = ScrollView(this)
        historyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyScroll.addView(historyContainer)
        root.addView(historyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadHistory()
        val startUri = intent.getStringExtra("video_uri") ?: VideoRecordStore.list(this).firstOrNull()?.uri
        if (!startUri.isNullOrBlank()) {
            val record = VideoRecordStore.list(this).firstOrNull { it.uri == startUri }
            play(Uri.parse(startUri), record?.title ?: "Seçili video")
        }
    }

    private fun loadHistory() {
        historyContainer.removeAllViews()
        val records = VideoRecordStore.list(this)
        if (records.isEmpty()) {
            historyContainer.addView(TextView(this).apply {
                text = "Henüz oluşturulmuş video yok. Ana ekrandan video oluşturabilir veya telefondan video seçebilirsin."
                setTextColor(Color.rgb(173, 191, 204))
                textSize = 13f
                setPadding(0, dp(6), 0, dp(10))
            })
            return
        }
        records.take(30).forEachIndexed { index, record ->
            historyContainer.addView(actionButton((index + 1).toString() + ". " + record.title) {
                play(Uri.parse(record.uri), record.title)
            })
        }
    }

    private fun play(uri: Uri, title: String) {
        activeUri = uri
        info.text = "Video açılıyor: " + title
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { exo ->
                    playerView.player = exo
                    exo.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_BUFFERING) info.text = "Video yükleniyor…"
                            if (playbackState == Player.STATE_READY) {
                                val size = exo.videoSize
                                info.text = if (size.width > 0 && size.height > 0) {
                                    "Görüntü hazır: " + size.width + "×" + size.height
                                } else "Ses hazır; bu dosyada görüntü akışı bulunamadı."
                            }
                        }
                        override fun onRenderedFirstFrame() {
                            info.text = "Görüntü oynatılıyor."
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            info.text = "Video oynatılamadı: " + error.errorCodeName + "\nBaşka bir video dosyası dene."
                        }
                    })
                }
            }
            player?.apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            info.text = "Video açılamadı: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun chooseVideo() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(pick, pickVideoRequest) }
        catch (_: Exception) { Toast.makeText(this, "Video seçici açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Use platform document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickVideoRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: Exception) { }
        play(uri, uri.lastPathSegment ?: "Telefondan seçilen video")
    }

    private fun actionButton(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.rgb(35, 95, 117))
            cornerRadius = dp(10).toFloat()
        }
        minHeight = dp(46)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4); bottomMargin = dp(4) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}