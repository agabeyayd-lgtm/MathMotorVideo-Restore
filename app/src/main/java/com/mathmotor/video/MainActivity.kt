package com.mathmotor.video

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import android.widget.MediaController
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var homeVideoView: VideoView
    private var latestUri: String? = null
    private val selectingVideoCode = 2002
    private val latestApkUrl = "https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest/download/MathMotorVideo.apk"
    private val latestReleaseApi = "https://api.github.com/repos/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest"
    private var pendingApkDownloadId = -1L
    private var updateReceiverRegistered = false
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != pendingApkDownloadId) return
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val apkUri = manager.getUriForDownloadedFile(id)
            if (apkUri == null) {
                status.text = "GÜNCELLEME İNDİRİLEMEDİ\\nİndirme bildirimini ve internet bağlantısını kontrol et."
                return
            }
            status.text = "İNDİRME TAMAMLANDI\\nAndroid kurulum ekranı açılıyor…"
            try {
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(install)
            } catch (e: Exception) {
                status.text = "APK indirildi ama kurulum ekranı açılamadı. İndirilenler klasöründen MathMotorVideo.apk dosyasını aç.\\n${e.message}"
            }
        }
    }

    override fun onCreate(kayıtInstanceState: Bundle?) {
        super.onCreate(kayıtInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        buildUi()
        latestUri = VideoRecordStore.list(this).firstOrNull()?.uri
        latestUri?.let { loadHomeVideo(it, false) }
        registerUpdateReceiver()
        requestRequiredPermissions()
        // Check for a published update automatically whenever the app opens.
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (!::homeVideoView.isInitialized) return
        val latest = VideoRecordStore.list(this).firstOrNull()?.uri
        if (!latest.isNullOrBlank() && latest != latestUri) {
            latestUri = latest
            loadHomeVideo(latest, false)
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT in 26..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isEmpty()) {
            importExistingVideos()
        } else {
            requestPermissions(permissions.distinct().toTypedArray(), 1001)
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1001) return
        val readPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(readPermission) == PackageManager.PERMISSION_GRANTED) {
            importExistingVideos()
        }
    }

    private fun importExistingVideos() {
        Thread {
            val imported = runCatching { VideoRecordStore.importPublicMovies(this) }.getOrDefault(0)
            val latest = VideoRecordStore.list(this).firstOrNull()?.uri
            runOnUiThread {
                if (!latest.isNullOrBlank()) {
                    if (latest != latestUri) loadHomeVideo(latest, false)
                    latestUri = latest
                }
                if (imported > 0) {
                    status.text = "ÖNCEKİ VİDEOLAR KURTARILDI\\n$imported video Movies/MathMotorVideo klasöründen geçmişe eklendi."
                }
            }
        }.start()
    }

    private fun buildUi() {
        val bg = Color.rgb(10, 18, 30)
        val surface = Color.rgb(22, 34, 51)
        val accent = Color.rgb(48, 190, 176)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(24))
        }
        scroll.addView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(label("MathMotor", 27f, Color.WHITE, true))
        heading.addView(label("VIDEO STÜDYOSU", 11f, Color.rgb(151, 174, 196), true))
        top.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(actionButton("↻") { checkForUpdates() }.apply {
            textSize = 23f
            minWidth = dp(48)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(48))
        })
        root.addView(top)
        root.addView(gap(16))

        root.addView(label("SON VİDEO", 12f, accent, true))
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(surface, 18)
        }
        homeVideoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setMediaController(MediaController(this@MainActivity))
        }
        // Keep the preview proportional to its actual width on narrow and large phones.
        previewCard.addView(homeVideoView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(190)
        ))
        previewCard.addView(TextView(this).apply {
            text = "Kayıtlı son videon burada görünür"
            textSize = 12f
            setTextColor(Color.rgb(165, 183, 201))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        })
        root.addView(previewCard, matchWrap())
        previewCard.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val previewWidth = (right - left - dp(20)).coerceAtLeast(dp(1))
            val desiredHeight = (previewWidth * 9f / 16f).toInt().coerceAtLeast(dp(120))
            val params = homeVideoView.layoutParams as? LinearLayout.LayoutParams
            if (params != null && params.height != desiredHeight) {
                params.height = desiredHeight
                homeVideoView.layoutParams = params
            }
        }

        root.addView(gap(18))
        root.addView(label("NE YAPMAK İSTİYORSUN?", 12f, Color.rgb(151, 174, 196), true))

        val editorButton = actionButton("✂   Video düzenle") {
            startActivity(Intent(this, VideoEditorActivity::class.java))
        }.apply {
            textSize = 17f
            minHeight = dp(62)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.rgb(105, 236, 218)),
                rounded(accent, 14),
                null
            )
            setTextColor(Color.rgb(8, 25, 36))
        }
        root.addView(editorButton, matchWrap())

        val chooseButton = actionButton("＋   Telefondan video seç  •  Kare testi") { chooseVideo() }.apply {
            minHeight = dp(56)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.rgb(73, 125, 160)),
                rounded(Color.rgb(35, 54, 77), 14),
                null
            )
        }
        root.addView(chooseButton, matchWrap())

        root.addView(gap(14))
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(surface, 14)
        }
        statusCard.addView(label("DURUM", 11f, accent, true))
        status = TextView(this).apply {
            setTextColor(Color.rgb(220, 231, 240))
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1f)
            text = "Hazır. Düzenlemek için üstteki ana düğmeye dokun."
        }
        statusCard.addView(status, matchWrap())
        root.addView(statusCard, matchWrap())
        setContentView(scroll)
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun loadHomeVideo(uri: String, autoplay: Boolean) {
        if (!::homeVideoView.isInitialized) return
        runCatching {
            homeVideoView.setVideoURI(Uri.parse(uri))
            homeVideoView.setOnPreparedListener { player ->
                player.isLooping = false
                if (autoplay) homeVideoView.start()
            }
        }.onFailure { if (::status.isInitialized) status.text = "Video oynatıcı açılamadı: ${it.message}" }
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, selectingVideoCode)
        } catch (e: Exception) {
            status.text = "Video seçici açılamadı: ${e.message}"
        }
    }

    @Deprecated("Use platform document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != selectingVideoCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: run { status.text = "Video seçilmedi."; return }
        status.text = "SEÇİLEN VİDEO ÇÖZÜMLENİYOR…\nBu işlem birkaç saniye sürebilir."
        Thread {
            val report = analyzeSelectedVideo(uri)
            runOnUiThread { status.text = report }
        }.start()
    }

    private fun analyzeSelectedVideo(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationUs = durationMs * 1000L
            if (durationMs <= 0L || width <= 0 || height <= 0) {
                return "VİDEO TESTİ BAŞARISIZ\nSüre veya görüntü boyutu okunamadı. Başka bir MP4/video dosyası dene."
            }
            val points = listOf(0L, durationUs / 4, durationUs / 2, durationUs * 3 / 4, (durationUs - 1).coerceAtLeast(0L)).distinct()
            var decoded = 0
            val signatures = mutableSetOf<Long>()
            val frameNotes = mutableListOf<String>()
            for ((index, timeUs) in points.withIndex()) {
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    decoded++
                    signatures.add(bitmapSignature(bitmap))
                    frameNotes.add("${index + 1}. örnek: ${bitmap.width}×${bitmap.height} piksel")
                    bitmap.recycle()
                } else {
                    frameNotes.add("${index + 1}. örnek: kare okunamadı")
                }
            }
            val displayWidth = if (rotation == 90 || rotation == 270) height else width
            val displayHeight = if (rotation == 90 || rotation == 270) width else height
            val aspect = displayWidth.toFloat() / displayHeight.toFloat()
            val portrait = aspect < 1f
            val readable = decoded == points.size
            val varied = signatures.size >= 2
            buildString {
                appendLine(if (readable) "VİDEO KARE TESTİ: BAŞARILI" else "VİDEO KARE TESTİ: UYARI")
                appendLine("Dosya: ${uri.lastPathSegment ?: "seçilen video"}")
                appendLine("Süre: ${durationMs / 1000.0} saniye")
                appendLine("Boyut: ${displayWidth}×${displayHeight} • döndürme: ${rotation}°")
                appendLine("Dikey 9:16'ya yakın: ${if (portrait && kotlin.math.abs(aspect - (9f / 16f)) < 0.12f) "EVET" else if (portrait) "DİKEY, oran farklı" else "HAYIR"}")
                appendLine("Örnek kareler: ${decoded}/${points.size} okunabildi")
                appendLine("Görüntü değişimi: ${if (varied) "örnek karelerde fark var" else "fark tespit edilemedi / sabit görüntü olabilir"}")
                appendLine()
                frameNotes.forEach { appendLine(it) }
                appendLine()
                appendLine("FİZİK NOTU: Bu kontrol video dosyasını ve örnek kareleri doğrular. Gerçek dünyadaki fizik kurallarını yalnızca piksellerden kesin olarak onaylamaz.")
                appendLine("Kaynak video değiştirilmedi.")
            }
        } catch (e: Exception) {
            "VİDEO KARE TESTİ BAŞARISIZ\n${e.javaClass.simpleName}: ${e.message ?: "Video okunamadı"}"
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun bitmapSignature(bitmap: Bitmap): Long {
        val x1 = (bitmap.width * 0.25f).toInt().coerceIn(0, bitmap.width - 1)
        val x2 = (bitmap.width * 0.50f).toInt().coerceIn(0, bitmap.width - 1)
        val x3 = (bitmap.width * 0.75f).toInt().coerceIn(0, bitmap.width - 1)
        val y1 = (bitmap.height * 0.25f).toInt().coerceIn(0, bitmap.height - 1)
        val y2 = (bitmap.height * 0.50f).toInt().coerceIn(0, bitmap.height - 1)
        val y3 = (bitmap.height * 0.75f).toInt().coerceIn(0, bitmap.height - 1)
        var hash = 1125899906842597L
        for (y in listOf(y1, y2, y3)) for (x in listOf(x1, x2, x3)) {
            hash = hash * 31 + bitmap.getPixel(x, y).toLong()
        }
        return hash
    }

    private fun registerUpdateReceiver() {
        if (updateReceiverRegistered) return
        try {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(updateReceiver, filter)
            updateReceiverRegistered = true
        } catch (_: Exception) { }
    }

    private fun checkForUpdates() {
        status.text = "GÜNCELLEME KONTROL EDİLİYOR…\\nGitHub sürümü denetleniyor."
        Thread {
            try {
                val connection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MathMotorVideo-Updater")
                }
                val json = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
                val release = JSONObject(json)
                val body = release.optString("body", "")
                val match = Regex("(?i)versionCode\\s*[=:]\\s*(\\d+)").find(body)
                val remoteCode = match?.groupValues?.getOrNull(1)?.toLongOrNull()
                val currentCode = if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(packageName, 0).longVersionCode else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                }
                val releaseName = release.optString("name", release.optString("tag_name", "Yeni sürüm"))
                runOnUiThread {
                    when {
                        remoteCode == null -> status.text = "SÜRÜM BİLGİSİ BULUNAMADI\\nYayın kaydında versionCode bilgisi yok. Biraz sonra tekrar dene."
                        remoteCode <= currentCode -> status.text = "UYGULAMA GÜNCEL\\nKurulu sürüm kodu: $currentCode\\nSon yayın: $releaseName"
                        else -> { status.text = "YENİ SÜRÜM BULUNDU\\nKurulu: $currentCode • Yeni: $remoteCode\\nAPK indiriliyor…"; downloadLatestApk() }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "GÜNCELLEME KONTROLÜ BAŞARISIZ\\nİnternet bağlantısını kontrol et.\\n${e.message ?: e.javaClass.simpleName}" }
            }
        }.start()
    }

    private fun downloadLatestApk() {
        try {
            val request = DownloadManager.Request(Uri.parse(latestApkUrl))
                .setTitle("MathMotor Video — Güncelleme")
                .setDescription("Yeni sürüm indiriliyor")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MathMotorVideo.apk")
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            pendingApkDownloadId = manager.enqueue(request)
            status.text = "APK ARKA PLANDA İNDİRİLİYOR\\nİndirme tamamlanınca Android kurulum ekranı açılacak. Kurulumu onaylaman gerekebilir."
        } catch (e: Exception) {
            status.text = "APK indirilemedi: ${e.message}\\nİnternet bağlantısını kontrol et."
        }
    }

    override fun onDestroy() {
        if (updateReceiverRegistered) { try { unregisterReceiver(updateReceiver) } catch (_: Exception) { }; updateReceiverRegistered = false }
        super.onDestroy()
    }

    private fun openVideoScreen() {
        val uri = latestUri ?: VideoRecordStore.list(this).firstOrNull()?.uri
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            if (!uri.isNullOrBlank()) putExtra("video_uri", uri)
        })
    }

    private fun openLatest() {
        val uri = latestUri ?: VideoRecordStore.list(this).firstOrNull()?.uri
        if (uri.isNullOrBlank()) {
            status.text = "Henüz oluşturulmuş video yok. Önce bir video oluştur."
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { status.text = "Video açılamadı: ${it.message}" }
    }

    private fun showHistory() {
        val records = VideoRecordStore.list(this)
        if (records.isEmpty()) { status.text = "VİDEO GEÇMİŞİ\nHenüz kayıtlı video yok."; return }
        status.text = buildString {
            appendLine("VİDEO GEÇMİŞİ • ${records.size} kayıt")
            records.take(20).forEachIndexed { i, record ->
                appendLine("${i + 1}. ${record.title}")
                appendLine("   seed=${record.seed} • ${DateFormat.getDateTimeInstance().format(Date(record.createdAt))}")
            }
            appendLine("\nEn son kaydı açmak için Son Videoyu Aç'a dokun.")
        }
        latestUri = records.first().uri
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun actionButton(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        val shape = GradientDrawable().apply {
            setColor(Color.rgb(35, 95, 117))
            cornerRadius = dp(10).toFloat()
        }
        background = RippleDrawable(
            ColorStateList.valueOf(Color.rgb(95, 180, 200)),
            shape,
            null
        )
        minHeight = dp(52)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isHapticFeedbackEnabled = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isEnabled = false
            animate().scaleX(0.97f).scaleY(0.97f).alpha(0.88f).setDuration(65)
                .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                    animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110)
                        .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                            isEnabled = true
                            action()
                        }.start()
                }.start()
        }
        layoutParams = matchWrap().apply { topMargin = dp(7); bottomMargin = dp(3) }
    }

    private fun gap(size: Int) = TextView(this).apply { height = dp(size) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}