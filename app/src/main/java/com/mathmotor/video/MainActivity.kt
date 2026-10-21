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
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 31, 45)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)
        root.addView(label("MATHMOTOR VIDEO", 25f, Color.WHITE, true))
        root.addView(label("YEREL VİDEO STÜDYOSU", 12f, Color.rgb(173, 191, 204), true))
        root.addView(gap(10))
        root.addView(label("VİDEO OYNATICI", 13f, Color.rgb(121, 202, 218), true))
        homeVideoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setMediaController(MediaController(this@MainActivity))
        }
        root.addView(homeVideoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply { topMargin = dp(4); bottomMargin = dp(12) })
        root.addView(actionButton("VİDEO EDİTÖRÜ • KES / YAZI / SES") { startActivity(Intent(this, VideoEditorActivity::class.java)) })
        root.addView(actionButton("TELEFONDAN VİDEO SEÇ / KARELERİ TEST ET") { chooseVideo() })
        root.addView(actionButton("GÜNCELLEMEYİ KONTROL ET") { checkForUpdates() })
        root.addView(gap(18))
        root.addView(label("DURUM / TEST KAYDI", 12f, Color.rgb(121, 202, 218), true))
        status = TextView(this).apply {
            setTextColor(Color.rgb(225, 233, 237)); textSize = 13f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(27, 48, 64))
            text = "Hazır. Telefonda video seç veya video editöründe kes, birleştir, yazı ve ses ekle."
        }
        root.addView(status, matchWrap().apply { topMargin = dp(8) })
        setContentView(scroll)
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