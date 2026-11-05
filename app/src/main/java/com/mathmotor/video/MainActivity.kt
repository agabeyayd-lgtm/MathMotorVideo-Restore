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
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
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
    private val saveDraftBackupRequest = 5107
    private val openDraftBackupRequest = 5108
    private val latestApkUrl = "https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest/download/MathMotorVideo.apk"
    private val latestReleaseApi = "https://api.github.com/repos/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest"

    private val bg = Color.rgb(247, 249, 253)
    private val panel = Color.WHITE
    private val panelRaised = Color.rgb(238, 245, 255)
    private val line = Color.rgb(224, 233, 245)
    private val accent = Color.rgb(18, 126, 242)
    private val muted = Color.rgb(105, 120, 145)
    private val white = Color.rgb(18, 28, 48)
    private val blueDeep = Color.rgb(11, 65, 184)
    private val blueMid = Color.rgb(13, 130, 241)
    private val blueLight = Color.rgb(22, 190, 238)
    private val pickImageRequest = 2003
    private val captureVideoRequest = 2004

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
        window.statusBarColor = blueDeep
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()) or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(this).apply {
            tag = "mathmotor_home_scroll"
            setBackgroundColor(Color.WHITE)
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        scroll.addView(page, ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Blue hero with MathMotor identity and a persistent updater control.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blueDeep, blueMid, blueLight)
            )
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "▶"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(blueMid)
            background = rounded(Color.WHITE, 13)
            layoutParams = LinearLayout.LayoutParams(dp(45), dp(45))
            contentDescription = "MathMotor logosu"
        })
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(7), 0)
            addView(TextView(this@MainActivity).apply {
                text = "MathMotor"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                includeFontPadding = false
            })
            addView(TextView(this@MainActivity).apply {
                text = "Video Düzenleyici"
                textSize = 11f
                setTextColor(Color.rgb(224, 241, 255))
            })
        }
        top.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val updateFrame = FrameLayout(this)
        val updateButton = TextView(this).apply {
            text = "⟳  Güncelleme"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(13, 45, 88))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(238, 248, 255), 24)
            isClickable = true
            isFocusable = true
            contentDescription = "Uygulama güncellemesini kontrol et"
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                checkForUpdates()
            }
        }
        updateFrame.addView(updateButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(42), Gravity.CENTER
        ))
        updateFrame.addView(View(this).apply {
            background = rounded(Color.rgb(255, 91, 103), 8)
            contentDescription = "Güncelleme kontrolü"
        }, FrameLayout.LayoutParams(dp(9), dp(9), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(2)
            rightMargin = dp(2)
        })
        top.addView(updateFrame)

        val profileIcon = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(38, 255, 255, 255))
                setStroke(dp(1), Color.rgb(222, 242, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(9) }
            contentDescription = "Profil ve uygulama bilgileri"
            isClickable = true
            isFocusable = true
            setOnClickListener { showProfilePanel() }
        }
        profileIcon.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }, FrameLayout.LayoutParams(dp(11), dp(11), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(8)
        })
        profileIcon.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }, FrameLayout.LayoutParams(dp(23), dp(14), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(5)
        })
        top.addView(profileIcon)
        hero.addView(top)

        updateStatus = TextView(this).apply {
            text = "Sürüm " + installedVersionCode() + "  •  Güncelleme kontrolü hazır"
            textSize = 9f
            setTextColor(Color.rgb(224, 241, 255))
            setPadding(dp(55), dp(3), 0, 0)
            maxLines = 2
        }
        hero.addView(updateStatus)
        hero.addView(gap(17))
        hero.addView(TextView(this).apply {
            text = "VIDEO CREATE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(218, 241, 255))
            letterSpacing = 0.08f
        })
        hero.addView(TextView(this).apply {
            text = "Hızlı Başlangıç  ›"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(1), 0, dp(12))
        })

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(homeActionCard(
            icon = "+",
            title = "Yeni video",
            subtitle = "Videonu düzenle",
            tint = Color.rgb(225, 246, 255),
            weight = 1.18f
        ) { chooseVideo() }, LinearLayout.LayoutParams(0, dp(132), 1.18f))
        actionRow.addView(gapWidth(10))
        actionRow.addView(homeActionCard(
            icon = "▧",
            title = "Fotoğrafı düzenle",
            subtitle = "Fotoğrafını düzenle",
            tint = Color.rgb(242, 240, 255),
            weight = 0.92f
        ) { choosePhoto() }, LinearLayout.LayoutParams(0, dp(132), 0.92f))
        hero.addView(actionRow)
        page.addView(hero, matchWrap())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        page.addView(body, matchWrap())

        val recentHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recentHeader.addView(TextView(this).apply {
            text = "Son Projeler"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 29, 48))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        recentHeader.addView(TextView(this).apply {
            text = "Tümünü Gör  ›"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(dp(8), dp(7), 0, dp(7))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, VideoPlayerActivity::class.java))
            }
        })
        body.addView(recentHeader)
        body.addView(gap(9))

        val recentScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        recentList = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recentScroll.addView(recentList, HorizontalScrollView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        body.addView(recentScroll, matchWrap())
        refreshRecentProjects()

        draftSectionGap = gap(12)
        body.addView(draftSectionGap)
        draftSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val draftsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("TASLAKLAR", 12f, white, true))
            addView(label("Kaldığın yerden devam et", 10f, muted, false))
        }
        draftSection.addView(draftsHeader)
        val backupActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        draftBackupButton = secondaryButton("Taslakları yedekle") { requestDraftBackup() }.apply {
            minHeight = dp(40)
            textSize = 10f
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        backupActions.addView(draftBackupButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backupActions.addView(gapWidth(6))
        backupActions.addView(secondaryButton("Yedekten geri yükle") { requestDraftRestore() }.apply {
            minHeight = dp(40)
            textSize = 10f
            setPadding(dp(8), dp(5), dp(8), dp(5))
        })
        draftSection.addView(gap(7))
        draftSection.addView(backupActions, matchWrap())
        draftSection.addView(label(
            "Yedek proje ayarlarını ve zaman çizelgesini saklar; kaynak video ve ses dosyalarını içermez.",
            9f, muted, false
        ))
        draftList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        draftSection.addView(gap(6))
        draftSection.addView(draftList, matchWrap())
        body.addView(draftSection, matchWrap())
        refreshDraftProjects()

        body.addView(gap(17))
        val toolsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolsHeader.addView(TextView(this).apply {
            text = "Araçlar"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 29, 48))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolsHeader.addView(TextView(this).apply {
            text = "Tümünü gör  ›"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(dp(8), dp(7), 0, dp(7))
            isClickable = true
            isFocusable = true
            setOnClickListener { scrollToHomeTools() }
        })
        body.addView(toolsHeader)
        body.addView(gap(8))
        toolGridContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(toolGridContainer, matchWrap())
        rebuildHomeToolGrid()

        body.addView(gap(11))
        body.addView(TextView(this).apply {
            text = "Videoların telefonunda kalır. Dosyaların MathMotor hesabına yüklenmez."
            textSize = 10f
            setTextColor(Color.rgb(137, 149, 166))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(3))
        })
        screen.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        screen.addView(buildBottomNavigation(), matchWrap())
        setContentView(screen)
    }

    private lateinit var toolGridContainer: LinearLayout

    private data class HomeTool(
        val icon: String,
        val title: String,
        val detail: String,
        val action: String
    )

    private val homeTools = listOf(
        HomeTool("✂", "AutoCut", "Videoyla başla", "video"),
        HomeTool("✧", "Rötuş yap", "Renk ve görüntü", "video"),
        HomeTool("✦", "Yapay zekâ oluşturucu", "Yapay zekâ araçları", "soon"),
        HomeTool("▧", "Fotoğraf araçları", "Fotoğraf katmanı", "photo"),
        HomeTool("◉", "Çekim ve kayıt", "Kamerayı aç", "camera"),
        HomeTool("✧", "Otomatik iyileştir", "Video düzenleme", "video"),
        HomeTool("▣", "Kapak oluşturma aracı", "Kapak karesi seç", "video"),
        HomeTool("CC", "Otomatik altyazı", "Metin düzenleme", "video"),
        HomeTool("✂", "YZ klip aracı", "Klip düzenleme", "video"),
        HomeTool("◉", "Arka planı kaldır", "Fotoğraf araçları", "soon"),
        HomeTool("▧", "Yapay zekâ fotoğraf", "Görsel oluşturma", "soon"),
        HomeTool("☁", "Alan", "Video kütüphanesi", "library"),
        HomeTool("◴", "Hızı ayarla", "Klip hızını düzenle", "video"),
        HomeTool("▣", "Pazarlama araçları", "Hazırlanıyor", "soon"),
        HomeTool("●", "Kayıt", "Kamerayla video çek", "camera"),
        HomeTool("♙", "Avatar araçları", "Hazırlanıyor", "soon"),
        HomeTool("▧", "Medya oluşturma", "Hazırlanıyor", "soon")
    )

    private fun homeActionCard(
        icon: String,
        title: String,
        subtitle: String,
        tint: Int,
        weight: Float,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(10), dp(11))
            background = rounded(tint, 20).apply {
                setStroke(dp(1), Color.argb(150, 255, 255, 255))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                action()
            }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = if (icon == "+") 33f else 28f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(10, 27, 52))
                gravity = Gravity.CENTER
                background = rounded(Color.WHITE, 12)
                layoutParams = LinearLayout.LayoutParams(dp(45), dp(45))
            })
            addView(gap(7))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = if (weight > 1f) 15f else 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(13, 24, 43))
                maxLines = 2
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 10f
                setTextColor(Color.rgb(80, 100, 130))
                maxLines = 1
            })
        }
    }

    private fun rebuildHomeToolGrid() {
        if (!::toolGridContainer.isInitialized) return
        toolGridContainer.removeAllViews()
        val visible = homeTools
        val rows = visible.chunked(3)
        rows.forEachIndexed { rowIndex, rowTools ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowTools.forEachIndexed { column, tool ->
                row.addView(homeToolTile(tool), LinearLayout.LayoutParams(0, dp(116), 1f))
                if (column != rowTools.lastIndex) row.addView(gapWidth(6))
            }
            repeat(3 - rowTools.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            toolGridContainer.addView(row, matchWrap())
            if (rowIndex != rows.lastIndex) toolGridContainer.addView(gap(3))
        }
        toolGridContainer.addView(TextView(this).apply {
            text = "Tüm araçlar (32)  ›"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { showToolNotReady("Tüm araçlar", "Ek araç kataloğu") }
        }, matchWrap())
    }

    private fun homeToolTile(tool: HomeTool): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
            isClickable = true
            isFocusable = true
            contentDescription = tool.title
            setOnClickListener { handleHomeTool(tool) }
        }
        cell.addView(TextView(this).apply {
            text = tool.icon
            textSize = if (tool.icon == "CC") 20f else 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(16, 38, 68))
            background = rounded(
                when (tool.title) {
                    "Rötuş yap", "Yapay zekâ oluşturucu", "Otomatik iyileştir" -> Color.rgb(237, 244, 255)
                    "Fotoğraf araçları", "Kapak oluşturma aracı" -> Color.rgb(237, 248, 255)
                    else -> Color.rgb(244, 247, 252)
                }, 18
            )
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        cell.addView(gap(5))
        cell.addView(TextView(this).apply {
            text = tool.title
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(25, 34, 54))
            gravity = Gravity.CENTER
            maxLines = 2
            minLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        return cell
    }

    private fun handleHomeTool(tool: HomeTool) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (tool.action) {
            "video" -> {
                Toast.makeText(this, tool.title + ": Düzenlemek için bir video seç.", Toast.LENGTH_SHORT).show()
                chooseVideo()
            }
            "photo" -> choosePhoto()
            "camera" -> captureVideo()
            "library" -> startActivity(Intent(this, VideoPlayerActivity::class.java))
            else -> showToolNotReady(tool.title, tool.detail)
        }
    }

    private fun showToolNotReady(title: String, detail: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(detail + " aracı MathMotor'a henüz eklenmedi. Ana düzenleme, video, kapak ve fotoğraf katmanı akışları kullanılabilir.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun buildBottomNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(5))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
        }
        val tabs = listOf(
            Triple("✂", "Düzenle", "home"),
            Triple("▣", "Şablonlar", "templates"),
            Triple("✧", "YZ Lab", "ai"),
            Triple("▱", "Kütüphane", "library"),
            Triple("♙", "Ben", "profile")
        )
        tabs.forEachIndexed { index, item ->
            val selected = index == 0
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(2))
                isClickable = true
                isFocusable = true
                contentDescription = item.second
                setOnClickListener {
                    when (item.third) {
                        "home" -> window.decorView.findViewWithTag<ScrollView>("mathmotor_home_scroll")?.smoothScrollTo(0, 0)
                        "templates" -> showToolNotReady("Şablonlar", "Şablon galerisi")
                        "ai" -> showToolNotReady("YZ Lab", "Yapay zekâ")
                        "library" -> startActivity(Intent(this@MainActivity, VideoPlayerActivity::class.java))
                        "profile" -> showProfilePanel()
                    }
                }
            }
            tab.addView(TextView(this).apply {
                text = item.first
                textSize = 23f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) accent else Color.rgb(151, 163, 181))
            })
            tab.addView(TextView(this).apply {
                text = item.second
                textSize = 9f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setTextColor(if (selected) accent else Color.rgb(151, 163, 181))
            })
            nav.addView(tab, LinearLayout.LayoutParams(0, dp(53), 1f))
        }
        return nav
    }

    private fun scrollToHomeTools() {
        if (!::toolGridContainer.isInitialized) return
        toolGridContainer.post {
            var current: View = toolGridContainer
            var offset = 0
            while (true) {
                offset += current.top
                val parent = current.parent
                if (parent is ScrollView) {
                    parent.smoothScrollTo(0, offset)
                    break
                }
                current = parent as? View ?: break
            }
        }
    }

    private fun installedVersionCode(): Long = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(399L)

    private fun showProfilePanel() {
        val version = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
        }.getOrDefault("399")
        android.app.AlertDialog.Builder(this)
            .setTitle("MathMotor")
            .setMessage("Video Düzenleyici\nSürüm " + version + "\n\nProjelerin telefonunda saklanır.")
            .setPositiveButton("Güncellemeyi kontrol et") { _, _ -> checkForUpdates() }
            .setNeutralButton("Kütüphane") { _, _ ->
                startActivity(Intent(this, VideoPlayerActivity::class.java))
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun choosePhoto() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(pick, pickImageRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "Fotoğraf seçici açılamadı: " + (e.message ?: "bilinmeyen hata"), Toast.LENGTH_LONG).show()
        }
    }

    private fun captureVideo() {
        try {
            startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), captureVideoRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "Kamera açılamadı: " + (e.message ?: "bilinmeyen hata"), Toast.LENGTH_LONG).show()
        }
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
        draftSection.visibility = if (allDrafts.isEmpty()) View.GONE else View.VISIBLE
        draftSectionGap.visibility = if (allDrafts.isEmpty()) View.GONE else View.VISIBLE
        draftBackupButton.isEnabled = allDrafts.isNotEmpty()
        draftBackupButton.alpha = if (allDrafts.isNotEmpty()) 1f else 0.45f
        if (drafts.isEmpty()) return
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
        val records = VideoRecordStore.list(this).take(8)
        if (records.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(Color.rgb(242, 247, 253), 16).apply {
                    setStroke(dp(1), line)
                }
                layoutParams = LinearLayout.LayoutParams(dp(132), dp(132))
                isClickable = true
                isFocusable = true
                setOnClickListener { chooseVideo() }
            }
            empty.addView(TextView(this).apply {
                text = "+"
                textSize = 27f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(accent)
                background = rounded(Color.WHITE, 12)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            empty.addView(TextView(this).apply {
                text = "Henüz proje yok"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(47, 62, 85))
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
            recentList.addView(empty)
            return
        }

        records.forEachIndexed { index, record ->
            val card = FrameLayout(this).apply {
                background = rounded(Color.rgb(229, 238, 249), 16)
                clipToOutline = true
                isClickable = true
                isFocusable = true
                contentDescription = "Videoyu aç: " + record.title
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                        putExtra("video_uri", record.uri)
                    })
                }
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(132)).apply {
                    if (index > 0) leftMargin = dp(9)
                }
            }
            val thumbnail = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(220, 232, 247))
                tag = record.uri
                contentDescription = record.title
            }
            card.addView(thumbnail, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            val bottomShade = View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.argb(210, 8, 18, 38))
                )
            }
            card.addView(bottomShade, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50), Gravity.BOTTOM
            ))
            val title = TextView(this).apply {
                text = record.title.ifBlank { "MathMotor projesi" }
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(8), 0, dp(6), dp(7))
                gravity = Gravity.BOTTOM or Gravity.START
            }
            card.addView(title, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM
            ))
            recentList.addView(card)
            loadRecentThumbnail(thumbnail, record.uri)
        }
    }

    private fun loadRecentThumbnail(target: ImageView, uriValue: String) {
        Thread {
            val bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, Uri.parse(uriValue))
                    retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
            if (bitmap != null) {
                mainHandler.post {
                    if (!isFinishing && target.tag == uriValue) target.setImageBitmap(bitmap)
                    else bitmap.recycle()
                }
            }
        }.start()
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
        if (requestCode == pickImageRequest && resultCode == RESULT_OK) {
            val imageUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            openPhotoEditor(imageUri)
            return
        }
        if (requestCode == captureVideoRequest && resultCode == RESULT_OK) {
            val captured = data?.data
            if (captured != null) openEditor(captured.toString())
            else Toast.makeText(this, "Kamera video döndürmedi.", Toast.LENGTH_LONG).show()
            return
        }
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
            putExtra("draft_id", VideoProjectDraftStore.newId())
            if (!uri.isNullOrBlank()) {
                putExtra("video_uri", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        })
    }

    private fun openPhotoEditor(uri: Uri) {
        startActivity(Intent(this, VideoEditorActivity::class.java).apply {
            putExtra("draft_id", VideoProjectDraftStore.newId())
            putExtra("photo_uri", uri.toString())
            putExtra("photo_editor_mode", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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