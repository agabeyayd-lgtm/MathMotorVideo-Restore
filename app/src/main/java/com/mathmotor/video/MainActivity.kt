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
import android.media.MediaMetadataRetriever
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    private lateinit var draftSection: LinearLayout
    private lateinit var draftList: LinearLayout
    private lateinit var draftSectionGap: View
    private lateinit var draftBackupButton: Button
    private lateinit var updateStatus: TextView
    private var pendingDraftBackup: JSONObject? = null
    private var updateDownloadId = -1L
    private var updateReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pickVideoRequest = 2002
    private val pickPhotoRequest = 2003
    private val saveDraftBackupRequest = 5107
    private val openDraftBackupRequest = 5108
    private val latestApkUrl = "https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest/download/MathMotorVideo.apk"
    private val latestReleaseApi = "https://api.github.com/repos/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest"

    private val bg = Color.rgb(8, 13, 22)
    private val panel = Color.rgb(16, 25, 38)
    private val panelRaised = Color.rgb(23, 36, 53)
    private val line = Color.rgb(43, 61, 80)
    private val accent = Color.rgb(64, 232, 198)
    private val muted = Color.rgb(174, 190, 207)
    private val white = Color.rgb(245, 248, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        syncGalleryRecords()
        registerUpdateReceiver()
        // Update checks stay manual so the home screen opens without a network request.
    }

    override fun onResume() {
        super.onResume()
        if (::recentList.isInitialized) syncGalleryRecords()
        if (::draftList.isInitialized) refreshDraftProjects()
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
        window.statusBarColor = Color.rgb(15, 76, 164)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE); clipToPadding = false; isFillViewport = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(18)) }
        scroll.addView(root)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(15,70,174), Color.rgb(17,145,220), Color.rgb(34,190,231))).apply {
                cornerRadii = floatArrayOf(0f,0f,0f,0f,dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat())
            }
        }
        val heroTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("MathMotor", 21f, Color.WHITE, true))
            addView(label("VIDEO STUDIO", 9f, Color.rgb(221,239,255), true))
        }
        heroTop.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroTop.addView(TextView(this).apply {
            text = "✦  Standart"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(21,45,72)); gravity = Gravity.CENTER
            setPadding(dp(13),dp(10),dp(13),dp(10)); background = rounded(Color.WHITE,20)
        })
        heroTop.addView(TextView(this).apply {
            text = "⌕"; textSize = 27f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(Color.rgb(35,92,175),50)
            layoutParams = LinearLayout.LayoutParams(dp(42),dp(42)).apply { leftMargin = dp(9) }
            contentDescription = "Ara"
            setOnClickListener { Toast.makeText(this@MainActivity,"Arama için Son çalışmalar bölümünü kullanabilirsin.",Toast.LENGTH_SHORT).show() }
        })
        hero.addView(heroTop); hero.addView(gap(24))
        hero.addView(label("Video oluştur",13f,Color.rgb(222,239,255),true))
        hero.addView(label("Başlamaya hazır mısın?",25f,Color.WHITE,true)); hero.addView(gap(15))
        val updateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(9),dp(9),dp(9)); background = rounded(Color.WHITE,15)
        }
        updateRow.addView(TextView(this).apply {
            text = "↻"; textSize = 22f; setTextColor(Color.rgb(17,105,181)); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34),dp(34))
        })
        val updateCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8),0,dp(5),0)
            addView(label("Uygulama güncellemesi",12f,Color.rgb(20,42,66),true))
            addView(label("Yeni sürümü kontrol et",10f,Color.rgb(91,111,132),false))
        }
        updateRow.addView(updateCopy,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        updateRow.addView(primaryButton("Güncelle") { checkForUpdates() }.apply {
            textSize=12f; minHeight=dp(40); setTextColor(Color.WHITE); background=rounded(Color.rgb(20,104,184),12)
        })
        hero.addView(updateRow)
        updateStatus = label("Güncelleme durumunu görmek için kontrol et.",10f,Color.WHITE,false).apply { setPadding(dp(2),dp(7),0,0); maxLines=2 }
        hero.addView(updateStatus); root.addView(hero,matchWrap()); root.addView(gap(14))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(homeActionCard("＋","Yeni video","Galeriden video seç") { chooseVideo() },LinearLayout.LayoutParams(0,dp(150),1f))
        actions.addView(gapWidth(10))
        actions.addView(homeActionCard("▧","Fotoğrafı\ndüzenle","Fotoğraf seç") { choosePhoto() },LinearLayout.LayoutParams(0,dp(150),0.78f))
        root.addView(actions,matchWrap())
        root.addView(secondaryButton("Boş proje oluştur  ›") { openEditor(null) }.apply {
            minHeight=dp(38); textSize=11f; setTextColor(Color.rgb(35,93,147))
            background=rounded(Color.rgb(244,249,253),14)
            contentDescription="Boş proje oluştur"
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(8) })
        root.addView(gap(22))

        val recentHeader = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        recentHeader.addView(label("Son çalışmalar",17f,Color.rgb(28,34,43),true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        recentHeader.addView(secondaryButton("Tümünü gör  ›") {
            startActivity(Intent(this,VideoPlayerActivity::class.java))
        }.apply { minHeight=dp(34); textSize=10f; setTextColor(Color.rgb(35,93,147)); background=rounded(Color.rgb(235,246,253),18) })
        root.addView(recentHeader); root.addView(gap(10))
        val recentScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; overScrollMode=View.OVER_SCROLL_NEVER }
        recentList = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        recentScroll.addView(recentList); root.addView(recentScroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(148)))
        refreshRecentProjects(); root.addView(gap(22))

        root.addView(label("Araçlar",18f,Color.rgb(28,34,43),true)); root.addView(gap(9))
        val tools = listOf(
            Triple("▣","AutoCut","Video düzenle"), Triple("✧","Rötuş yap","Fotoğraf araçları"), Triple("✦","Yapay zekâ\noluşturucu","Üretim araçları"),
            Triple("▧","Fotoğraf araçları","Fotoğraf seç"), Triple("◉","Çekim ve kayıt","Kamera"), Triple("✥","Otomatik\niyileştir","Görüntü araçları"),
            Triple("▱","Kapak oluşturma\naracı","Kapak tasarla"), Triple("▣","Otomatik alt\nyazı","Altyazı araçları"), Triple("✂","YZ klip aracı","Klip araçları"),
            Triple("▧","Arka planı kaldır","Fotoğraf araçları"), Triple("▤","YZ fotoğraf","Fotoğraf üret"), Triple("◌","Alan","Alan araçları"),
            Triple("◴","Hızı ayarla","Hız araçları"), Triple("▣","Pazarlama araçları","Şablonlar"), Triple("♩","Kayıt","Ses kaydı"),
            Triple("♙","Avatar araçları","Avatar"), Triple("▧","Medya oluşturma","Medya araçları"), Triple("⠿","Tüm araçlar (32)","Araç kitaplığı")
        )
        tools.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            rowItems.forEachIndexed { index,item ->
                val action: () -> Unit = when(item.second) {
                    "AutoCut" -> ({ chooseVideo() })
                    "Fotoğraf araçları","Arka planı kaldır" -> ({ choosePhoto() })
                    "Çekim ve kayıt","Kayıt" -> ({ launchCapture() })
                    "Kapak oluşturma\naracı" -> ({ choosePhoto() })
                    else -> ({ Toast.makeText(this, "${item.second.replace('\n',' ')} henüz etkin değil.",Toast.LENGTH_SHORT).show() })
                }
                row.addView(homeToolTile(item.first,item.second,action),LinearLayout.LayoutParams(0,dp(116),1f).apply { if(index>0) leftMargin=dp(7) })
            }
            repeat(3-rowItems.size) { row.addView(View(this),LinearLayout.LayoutParams(0,dp(116),1f)) }
            root.addView(row,matchWrap()); root.addView(gap(7))
        }

        root.addView(gap(13))
        draftSectionGap=gap(8)
        draftSection=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        val draftsHeader=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            addView(label("TASLAKLAR",17f,Color.rgb(28,34,43),true))
            addView(label("Kaldığın yerden devam et",10f,Color.rgb(110,122,137),false))
        }
        draftSection.addView(draftsHeader)
        val backupActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        draftBackupButton=secondaryButton("Taslakları yedekle") { requestDraftBackup() }.apply { minHeight=dp(40); textSize=10f }
        backupActions.addView(draftBackupButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        backupActions.addView(gapWidth(6))
        backupActions.addView(secondaryButton("Yedekten geri yükle") { requestDraftRestore() }.apply { minHeight=dp(40); textSize=10f })
        draftSection.addView(gap(8)); draftSection.addView(backupActions,matchWrap()); draftSection.addView(gap(4))
        draftSection.addView(label("Yedek proje ayarlarını ve zaman çizelgesini saklar; kaynak medya dosyalarını içermez.",9f,Color.rgb(110,122,137),false))
        draftList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        draftSection.addView(gap(6)); draftSection.addView(draftList,matchWrap())
        root.addView(draftSection,matchWrap()); refreshDraftProjects(); root.addView(gap(10))

        val footer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(0,dp(8),0,dp(5)) }
        listOf("✂" to "Düzenle","▥" to "Şablonlar","✥" to "YZ Lab","▱" to "Kütüphane","♙" to "Ben").forEachIndexed { index,pair ->
            footer.addView(homeNavItem(pair.first,pair.second,index==0) {
                when(index) {
                    0 -> chooseVideo()
                    3 -> startActivity(Intent(this,VideoPlayerActivity::class.java))
                    else -> Toast.makeText(this,"${pair.second} bölümü henüz etkin değil.",Toast.LENGTH_SHORT).show()
                }
            },LinearLayout.LayoutParams(0,dp(58),1f))
        }
        screen.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        screen.addView(footer,matchWrap()); setContentView(screen)
    }

    private fun homeActionCard(icon:String,title:String,subtitle:String,action:()->Unit):View =
        LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(10),dp(12),dp(10),dp(10))
            background=ripple(Color.rgb(224,243,253),Color.rgb(211,235,249),18)
            isClickable=true; isFocusable=true; setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply { text=icon; textSize=30f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(9,28,47)); gravity=Gravity.CENTER })
            addView(gap(5))
            addView(label(title,16f,Color.rgb(15,25,38),true).apply { gravity=Gravity.CENTER; textAlignment=View.TEXT_ALIGNMENT_CENTER })
            addView(label(subtitle,10f,Color.rgb(95,112,130),false).apply { gravity=Gravity.CENTER })
        }

    private fun homeToolTile(icon:String,title:String,action:()->Unit):View =
        LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(5),dp(8),dp(5),dp(7))
            background=ripple(Color.WHITE,Color.rgb(245,247,250),14); isClickable=true; isFocusable=true
            contentDescription=title.replace('\n',' '); setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply { text=icon; textSize=25f; setTextColor(Color.rgb(44,49,58)); gravity=Gravity.CENTER })
            addView(gap(7))
            addView(label(title,12f,Color.rgb(36,40,48),true).apply { gravity=Gravity.CENTER; textAlignment=View.TEXT_ALIGNMENT_CENTER; maxLines=2 })
        }

    private fun homeNavItem(icon:String,title:String,selected:Boolean,action:()->Unit):View =
        LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; isClickable=true; isFocusable=true; setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply { text=icon; textSize=22f; setTextColor(if(selected) Color.BLACK else Color.rgb(155,165,176)); gravity=Gravity.CENTER })
            addView(label(title,10f,if(selected) Color.BLACK else Color.rgb(155,165,176),selected).apply { gravity=Gravity.CENTER })
        }

    private fun choosePhoto() {
        val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(pick,pickPhotoRequest) }
        catch(error:Exception) { Toast.makeText(this,"Fotoğraf seçici açılamadı: ${error.message}",Toast.LENGTH_LONG).show() }
    }

    private fun launchCapture() {
        try { startActivityForResult(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE),pickVideoRequest) }
        catch(_:Exception) { Toast.makeText(this,"Kamera açılamadı.",Toast.LENGTH_SHORT).show() }
    }

    /**
     * Rebuild the app's recent-video list from files already saved to the phone's
     * Movies/MathMotorVideo gallery. Duplicate URIs are ignored by VideoRecordStore.
     */
    private var gallerySyncRunning = false

    /**
     * MediaStore can be slow on a cold start. Import in the background so the
     * home screen stays responsive, then update the gallery on the UI thread.
     */
    private fun syncGalleryRecords() {
        if (gallerySyncRunning) return
        gallerySyncRunning = true
        Thread {
            runCatching { VideoRecordStore.importPublicMovies(applicationContext) }
            mainHandler.post {
                gallerySyncRunning = false
                if (!isFinishing && ::recentList.isInitialized) refreshRecentProjects()
            }
        }.start()
    }

    private fun refreshDraftProjects() {
        if (!::draftSection.isInitialized || !::draftList.isInitialized) return
        val allDrafts = VideoProjectDraftStore.list(this)
        val drafts = allDrafts.take(5)
        draftList.removeAllViews()
        draftSection.visibility = View.VISIBLE
        draftSectionGap.visibility = if (allDrafts.isEmpty()) View.GONE else View.VISIBLE
        draftBackupButton.isEnabled = allDrafts.isNotEmpty()
        draftBackupButton.alpha = if (allDrafts.isNotEmpty()) 1f else 0.45f
        if (drafts.isEmpty()) {
            draftList.addView(label("Henüz kaydedilmiş taslak yok.", 10f, muted, false).apply {
                setPadding(0, dp(7), 0, dp(2))
            })
            return
        }
        drafts.forEachIndexed { index, draft ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(10), dp(9))
                background = ripple(panel, line, 14)
                isClickable = true
                isFocusable = true
                contentDescription = "Taslağa devam et: ${draft.title}"
                setOnClickListener { openDraftEditor(draft.id) }
            }
            val mark = TextView(this).apply {
                text = "✂"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accent)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(18, 49, 58), 10)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            }
            item.addView(mark)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
                addView(label(draft.title.ifBlank { "Video taslağı" }, 12f, white, true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(label(
                    "${draft.clipCount} klip • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(draft.savedAt))}",
                    10f, muted, false
                ).apply { maxLines = 1 })
            }
            item.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            item.addView(secondaryButton("Devam et") { openDraftEditor(draft.id) }.apply {
                minHeight = dp(38)
                minWidth = dp(74)
                textSize = 10f
                setPadding(dp(7), dp(3), dp(7), dp(3))
            })
            draftList.addView(item, matchWrap())
            if (index != drafts.lastIndex) draftList.addView(gap(6))
        }
    }

    private fun refreshRecentProjects() {
        recentList.removeAllViews()
        val records=VideoRecordStore.list(this).take(12)
        if(records.isEmpty()) {
            val empty=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(15),dp(12),dp(15),dp(12))
                background=rounded(Color.rgb(244,248,251),15)
            }
            empty.addView(label("▣",25f,Color.rgb(125,145,163),true).apply { gravity=Gravity.CENTER })
            empty.addView(label("Henüz çalışma yok",12f,Color.rgb(62,76,91),true).apply { gravity=Gravity.CENTER })
            empty.addView(label("Dışa aktardığın videolar burada görünür.",10f,Color.rgb(110,122,137),false).apply { gravity=Gravity.CENTER })
            recentList.addView(empty,LinearLayout.LayoutParams(dp(230),dp(132))); return
        }
        records.forEachIndexed { index,record ->
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL; background=rounded(Color.rgb(241,246,250),13); clipToOutline=true
                isClickable=true; isFocusable=true; contentDescription="Videoyu aç: ${record.title}"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity,VideoPlayerActivity::class.java).apply { putExtra("video_uri",record.uri) })
                }
            }
            val preview=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.rgb(199,216,229)); contentDescription=record.title }
            card.addView(preview,LinearLayout.LayoutParams(dp(142),dp(91)))
            card.addView(label(record.title.ifBlank { "Düzenlenmiş video" },10f,Color.rgb(35,43,52),true).apply {
                maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(dp(7),dp(6),dp(7),dp(1))
            })
            card.addView(label(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(record.createdAt)),9f,Color.rgb(105,117,130),false).apply {
                setPadding(dp(7),0,dp(7),dp(5))
            })
            recentList.addView(card,LinearLayout.LayoutParams(dp(142),dp(132)).apply { if(index>0) leftMargin=dp(9) })
            Thread {
                val bitmap=runCatching {
                    val retriever=MediaMetadataRetriever()
                    try { retriever.setDataSource(this,Uri.parse(record.uri)); retriever.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
                    finally { retriever.release() }
                }.getOrNull()
                if(bitmap!=null) mainHandler.post {
                    if(!isFinishing && preview.isAttachedToWindow) preview.setImageBitmap(bitmap) else bitmap.recycle()
                }
            }.start()
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

    private fun requestDraftBackup() {
        val backup = runCatching { VideoProjectDraftStore.createBackup(this) }.getOrElse {
            Toast.makeText(this, it.message ?: "Taslak yedeği hazırlanamadı.", Toast.LENGTH_LONG).show()
            return
        }
        pendingDraftBackup = backup
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "MathMotor_Taslak_Yedegi.json")
        }
        try {
            startActivityForResult(intent, saveDraftBackupRequest)
        } catch (error: Exception) {
            pendingDraftBackup = null
            Toast.makeText(this, "Yedek kaydetme ekranı açılamadı: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestDraftRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, openDraftBackupRequest)
        } catch (error: Exception) {
            Toast.makeText(this, "Yedek seçme ekranı açılamadı: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == saveDraftBackupRequest) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            val backup = pendingDraftBackup
            pendingDraftBackup = null
            if (uri == null || backup == null) return
            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(backup.toString(2))
                } ?: error("Yedek dosyası açılamadı.")
            }.onSuccess {
                Toast.makeText(this, "Taslak yedeği kaydedildi.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this, "Yedek kaydedilemedi: ${it.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == openDraftBackupRequest) {
            if (resultCode != RESULT_OK) return
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            runCatching {
                val json = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 50 * 1024 * 1024) { "Yedek dosyası 50 MB sınırını aşıyor." }
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), Charsets.UTF_8)
                } ?: error("Yedek dosyası okunamadı.")
                VideoProjectDraftStore.restoreBackup(this, JSONObject(json))
            }.onSuccess { result ->
                refreshDraftProjects()
                Toast.makeText(
                    this,
                    "${result.restoredCount} taslak geri yüklendi; ${result.skippedCount} kayıt atlandı. Kaynak medya dosyalarını gerekirse yeniden seç.",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(this, "Yedek geri yüklenemedi: ${it.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (resultCode != RESULT_OK) return
        if (requestCode == pickPhotoRequest) {
            val photoUri=data?.data ?: return
            try { contentResolver.takePersistableUriPermission(photoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) { }
            startActivity(Intent(this,VideoEditorActivity::class.java).apply {
                putExtra("draft_id",VideoProjectDraftStore.newId())
                putExtra("initial_overlay_uri",photoUri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            return
        }
        if (requestCode != pickVideoRequest) return
        val uri=data?.data ?: run {
            Toast.makeText(this,"Video seçilmedi.",Toast.LENGTH_SHORT).show()
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
            putExtra("draft_id", VideoProjectDraftStore.newId())
            if (!uri.isNullOrBlank()) {
                putExtra("video_uri", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
    }

    private fun openDraftEditor(id: String) {
        if (!VideoProjectDraftStore.isValidId(id)) return
        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            putExtra("draft_id", id)
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