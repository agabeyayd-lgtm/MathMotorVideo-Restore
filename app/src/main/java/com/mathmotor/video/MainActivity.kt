package com.mathmotor.video

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * A local-first home screen for the MathMotorVideo editor.
 * Video selection uses Android's document picker; it does not upload files or call an AI API.
 */
class MainActivity : Activity() {
    private lateinit var recentList: LinearLayout
    private lateinit var updateStatus: TextView
    private var updateDownloadId = -1L
    private var updateReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pickVideoRequest = 2002
    private val latestApkUrl = "https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest/download/MathMotorVideo.apk"
    private val latestReleaseApi = "https://api.github.com/repos/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest"

    private val bg = Color.rgb(9, 15, 25)
    private val panel = Color.rgb(19, 29, 43)
    private val panelRaised = Color.rgb(25, 38, 54)
    private val line = Color.rgb(42, 59, 77)
    private val accent = Color.rgb(57, 220, 190)
    private val muted = Color.rgb(154, 174, 195)
    private val white = Color.rgb(242, 247, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        syncGalleryRecords()
        registerUpdateReceiver()
        // Preserve the previous behavior: check for a newer published version on launch.
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (::recentList.isInitialized) syncGalleryRecords()
    }

    override fun onDestroy() {
        if (updateReceiverRegistered) {
            try { unregisterReceiver(updateReceiver) } catch (_: Exception) { }
            updateReceiverRegistered = false
        }
        super.onDestroy()
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != updateDownloadId) return
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) return
                when (cursor.getInt(statusIndex)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        updateStatus.text = "İndirme tamamlandı. Kurulum ekranı açılıyor…"
                        val apkUri = manager.getUriForDownloadedFile(id)
                        if (apkUri != null) {
                            val install = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { startActivity(install) }
                            catch (_: Exception) { updateStatus.text = "APK indirildi. İndirilenler bölümünden açıp kurulumu başlat." }
                        } else updateStatus.text = "APK indirildi ama dosya açılamadı."
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                        updateStatus.text = "İndirme başarısız (kod " + reason + "). İnternet bağlantını kontrol et."
                    }
                }
            }
        }
    }

    private fun registerUpdateReceiver() {
        if (updateReceiverRegistered) return
        try {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(updateReceiver, filter)
            }
            updateReceiverRegistered = true
        } catch (_: Exception) {
            updateStatus.text = "Güncelleme bildirimi hazırlanamadı."
        }
    }

    /**
     * Keep the original updater behavior: compare the published versionCode
     * before downloading anything. This avoids redownloading the same APK.
     */
    private fun checkForUpdates() {
        updateStatus.text = "Güncelleme kontrol ediliyor… GitHub sürümü denetleniyor."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MathMotorVideo-Updater")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub sunucusu ${connection.responseCode} döndürdü")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val body = release.optString("body", "")
                val match = Regex("(?i)versionCode\\s*[=:]\\s*(\\d+)").find(body)
                val remoteCode = match?.groupValues?.getOrNull(1)?.toLongOrNull()
                val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                }
                val releaseName = release.optString("name", release.optString("tag_name", "Yeni sürüm"))
                mainHandler.post {
                    when {
                        remoteCode == null -> {
                            updateStatus.text = "Sürüm bilgisi bulunamadı. Yayın kaydında versionCode yok."
                        }
                        remoteCode <= currentCode -> {
                            updateStatus.text = "Uygulama güncel. Kurulu sürüm: $currentCode • Son yayın: $releaseName"
                        }
                        else -> {
                            updateStatus.text = "Yeni sürüm bulundu. Kurulu: $currentCode • Yeni: $remoteCode. APK indiriliyor…"
                            downloadLatestApk()
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    updateStatus.text = "Güncelleme kontrol edilemedi: ${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun downloadLatestApk() {
        try {
            registerUpdateReceiver()
            val request = DownloadManager.Request(Uri.parse(latestApkUrl))
                .setTitle("MathMotor Video — Güncelleme")
                .setDescription("Yeni sürüm indiriliyor")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MathMotorVideo.apk")
            updateDownloadId = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            updateStatus.text = "APK indiriliyor. Bildirimlerden ilerlemeyi görebilirsin; bitince kurulum ekranı açılır."
        } catch (e: Exception) {
            updateStatus.text = "APK indirilemedi: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        scroll.addView(root)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "MM"
            textSize = 14f
            setTextColor(Color.rgb(7, 25, 31))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(accent, 13)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        topBar.addView(mark)
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
            addView(label("MathMotor Video", 19f, white, true))
            addView(label("MOBİL VİDEO STÜDYOSU", 10f, muted, true))
        }
        topBar.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(iconButton("＋") { openEditor(null) }.apply {
            contentDescription = "Yeni düzenleme başlat"
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        root.addView(topBar)
        root.addView(gap(14))

        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, 15).apply { setStroke(dp(1), line) }
            addView(label("UYGULAMA GÜNCELLEMESİ", 11f, accent, true))
            addView(label("Yeni sürümü uygulamanın içinden kontrol et.", 12f, muted, false))
            addView(gap(7))
            addView(secondaryButton("↻   Güncellemeleri kontrol et") { checkForUpdates() }, matchWrap())
            updateStatus = label("Güncelleme kontrol edilmedi.", 11f, muted, false)
            updateStatus.setPadding(0, dp(6), 0, 0)
            addView(updateStatus)
        }
        root.addView(updateCard, matchWrap())
        root.addView(gap(18))
        root.addView(gap(26))

        root.addView(label("KURGU MASASI", 11f, accent, true))
        root.addView(label("Videonu düzenlemeye başla.", 27f, white, true))
        root.addView(label("Kes, birleştir, ses ve yazı ekle. Sonucu MP4 olarak kaydet.", 14f, muted, false).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        root.addView(gap(18))

        val startCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
            background = rounded(panelRaised, 20).apply {
                setStroke(dp(1), line)
            }
        }
        val startTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val startIcon = TextView(this).apply {
            text = "✂"
            textSize = 27f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(19, 65, 65), 14)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        startTop.addView(startIcon)
        val startCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("YENİ DÜZENLEME", 15f, white, true))
            addView(label("Bir video seç ve doğrudan editöre geç.", 12f, muted, false))
        }
        startTop.addView(startCopy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        startCard.addView(startTop)
        startCard.addView(gap(16))
        val choose = primaryButton("＋   Galeriden video seç") { chooseVideo() }.apply {
            minHeight = dp(58)
            textSize = 16f
        }
        startCard.addView(choose, matchWrap())
        startCard.addView(gap(8))
        startCard.addView(secondaryButton("Boş düzenleyiciyi aç   ›") { openEditor(null) }, matchWrap())
        root.addView(startCard, matchWrap())

        root.addView(gap(25))
        root.addView(label("DÜZENLEME ARAÇLARI", 11f, accent, true))
        root.addView(gap(5))
        val toolRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rowOne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowOne.addView(toolCard("01", "Kes ve birleştir", "Klipleri sırala, böl ve kırp"), weightedCardParams())
        rowOne.addView(gapWidth(9))
        rowOne.addView(toolCard("02", "Metin katmanı", "Yazının süresini ve yerini ayarla"), weightedCardParams())
        toolRows.addView(rowOne)
        toolRows.addView(gap(9))
        val rowTwo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowTwo.addView(toolCard("03", "Ses ve müzik", "Ses seviyesini ve başlangıcını ayarla"), weightedCardParams())
        rowTwo.addView(gapWidth(9))
        rowTwo.addView(toolCard("04", "MP4 dışa aktar", "Projeyi kaydet veya videoyu çıkar"), weightedCardParams())
        toolRows.addView(rowTwo)
        root.addView(toolRows, matchWrap())

        root.addView(gap(26))
        val recentHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val recentTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("SON DIŞA AKTARILANLAR", 12f, white, true))
            addView(label("Telefonunda oluşturduğun videolar", 11f, muted, false))
        }
        recentHeader.addView(recentTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        recentHeader.addView(secondaryButton("↻ Galeriyi yenile") {
            syncGalleryRecords()
            Toast.makeText(this, "Telefon galerisindeki videolar kontrol edildi.", Toast.LENGTH_SHORT).show()
        }.apply { minHeight = dp(40); textSize = 11f })
        recentHeader.addView(secondaryButton("Tümünü aç") {
            startActivity(Intent(this, VideoPlayerActivity::class.java))
        }.apply { minHeight = dp(40); textSize = 11f })
        root.addView(recentHeader)
        root.addView(gap(10))
        recentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recentList, matchWrap())

        root.addView(gap(22))
        val footer = TextView(this).apply {
            text = "YEREL DÜZENLEME   •   YAPAY ZEKÂ YOK   •   ÜCRETLİ API YOK"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(112, 137, 159))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(footer)
        setContentView(scroll)
    }

    /**
     * Rebuild the app's recent-video list from files already saved to the phone's
     * Movies/MathMotorVideo gallery. Duplicate URIs are ignored by VideoRecordStore.
     */
    private fun syncGalleryRecords() {
        runCatching { VideoRecordStore.importPublicMovies(this) }
        if (::recentList.isInitialized) refreshRecentProjects()
    }

    private fun refreshRecentProjects() {
        recentList.removeAllViews()
        val records = VideoRecordStore.list(this).take(5)
        if (records.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(15), dp(14), dp(15))
                background = rounded(panel, 14).apply { setStroke(dp(1), line) }
            }
            val icon = label("▣", 23f, muted, true)
            empty.addView(icon, LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT))
            val copy = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Henüz dışa aktarılan video yok", 13f, white, true))
                addView(label("İlk kurgunu oluşturduğunda burada görünecek.", 11f, muted, false))
            }
            empty.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            recentList.addView(empty, matchWrap())
            return
        }

        records.forEachIndexed { index, record ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = ripple(panel, line, 14)
                isClickable = true
                isFocusable = true
                contentDescription = "Videoyu aç: " + record.title
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                        putExtra("video_uri", record.uri)
                    })
                }
            }
            val number = TextView(this).apply {
                text = String.format("%02d", index + 1)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accent)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(18, 49, 58), 10)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            }
            item.addView(number)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), 0, dp(6), 0)
                addView(label(record.title.ifBlank { "Düzenlenmiş video" }, 13f, white, true))
                addView(label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.createdAt)), 11f, muted, false))
            }
            item.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            item.addView(label("›", 24f, muted, false))
            recentList.addView(item, matchWrap())
            if (index != records.lastIndex) recentList.addView(gap(7))
        }
    }

    private fun chooseVideo() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(pick, pickVideoRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "Video seçici açılamadı: " + (e.message ?: "bilinmeyen hata"), Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickVideoRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: run {
            Toast.makeText(this, "Video seçilmedi.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some document providers grant temporary access only; the editor can still open it now.
        }
        openEditor(uri.toString())
    }

    private fun openEditor(uri: String?) {
        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            if (!uri.isNullOrBlank()) {
                putExtra("video_uri", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
    }

    private fun toolCard(number: String, title: String, detail: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(13), dp(12), dp(14))
            background = rounded(panel, 15).apply { setStroke(dp(1), line) }
            addView(label(number, 10f, accent, true))
            addView(gap(6))
            addView(label(title, 13f, white, true))
            addView(gap(3))
            addView(label(detail, 11f, muted, false).apply { setLineSpacing(dp(2).toFloat(), 1f) })
        }
    }

    private fun primaryButton(value: String, action: () -> Unit): Button = styledButton(value, true, action)

    private fun secondaryButton(value: String, action: () -> Unit): Button = styledButton(value, false, action)

    private fun iconButton(value: String, action: () -> Unit): Button = styledButton(value, false, action)

    private fun styledButton(value: String, primary: Boolean, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.rgb(7, 25, 31) else white)
        val fill = if (primary) accent else panelRaised
        val shape = rounded(fill, 12)
        background = RippleDrawable(
            ColorStateList.valueOf(if (primary) Color.rgb(145, 255, 230) else Color.rgb(70, 105, 130)),
            shape,
            null
        )
        minHeight = dp(46)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        isHapticFeedbackEnabled = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            action()
        }
    }

    private fun ripple(fill: Int, stroke: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.rgb(53, 83, 103)),
        rounded(fill, radius).apply { setStroke(dp(1), stroke) },
        null
    )

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(3))
    }

    private fun gap(size: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(size)) }
    private fun gapWidth(size: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(size), 1) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun weightedCardParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}