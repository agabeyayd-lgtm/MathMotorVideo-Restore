package com.mathmotor.video

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
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
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var scenarioText: TextView
    private var scenario: HumanScenario = ScenarioEngine.generate(System.currentTimeMillis())
    private var latestUri: String? = null
    private val rendering = AtomicBoolean(false)
    private val selectingVideoCode = 2002
    private val latestApkUrl = "https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/releases/latest/download/MathMotorVideo.apk"

    override fun onCreate(kayıtInstanceState: Bundle?) {
        super.onCreate(kayıtInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        if (Build.VERSION.SDK_INT in 26..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }
        buildUi()
        latestUri = VideoRecordStore.list(this).firstOrNull()?.uri
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 31, 45)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)
        root.addView(label("MATHMOTOR VIDEO", 25f, Color.WHITE, true))
        root.addView(label("YEREL STÜDYO  •  9:16  •  10 SANİYE", 12f, Color.rgb(173, 191, 204), true))
        root.addView(gap(18))
        root.addView(label("MEVCUT SENARYO", 12f, Color.rgb(121, 202, 218), true))
        scenarioText = label("", 18f, Color.WHITE, true)
        root.addView(scenarioText)
        root.addView(label("Önce fiziksel zaman çizelgesi oluşturulur. Görsel yönetmen, durumları değiştirmeden bunu kullanır.", 14f, Color.rgb(198, 211, 218), false))
        root.addView(gap(18))
        root.addView(actionButton("YENİ DÜNYA / YENİ SENARYO") { createNewScenario() })
        root.addView(actionButton("10 SANİYELİK VİDEO OLUŞTUR") { createVideo() })
        root.addView(actionButton("FİZİK / KARE TESTİ  •  300 KARE") { runPhysicsTest() })
        root.addView(actionButton("TELEFONDAN VİDEO SEÇ VE TEST ET") { chooseVideo() })
        root.addView(actionButton("GÜNCEL APK'YI İNDİR") { downloadLatestApk() })
        root.addView(actionButton("SON VİDEOYU AÇ") { openLatest() })
        root.addView(actionButton("VİDEO GEÇMİŞİ") { showHistory() })
        root.addView(gap(18))
        root.addView(label("DURUM / TEST KAYDI", 12f, Color.rgb(121, 202, 218), true))
        status = TextView(this).apply {
            setTextColor(Color.rgb(225, 233, 237)); textSize = 13f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(27, 48, 64))
            text = "Hazır. Senaryo oluştur, video üret veya telefondan bir video seçip kare testini çalıştır."
        }
        root.addView(status, matchWrap().apply { topMargin = dp(8) })
        root.addView(gap(18))
        root.addView(label("YENİDEN OLUŞTURULAN SÜRÜM", 12f, Color.rgb(121, 202, 218), true))
        root.addView(label("Bu sürüm çevrimdışı üretim, video oluşturma, geçmiş ve kare testi altyapısını yeniden kurar. Orijinal V7 kaynak arşivi bulunamadı; V7'deki harici sosyal veri bağlantıları henüz eklenmedi.", 12f, Color.rgb(173, 191, 204), false))
        setContentView(scroll)
        updateScenarioText()
    }

    private fun createNewScenario() {
        scenario = ScenarioEngine.generate(System.currentTimeMillis())
        updateScenarioText()
        status.text = "DÜNYA OLUŞTURULDU\nseed=${scenario.seed}\nframes=${scenario.world.frameCount}\nkind=${scenario.kind.label}\nPHYSX=${if (MathEngine.validate(scenario.world).isEmpty()) "GEÇERLİ" else "GEÇERSİZ"}"
    }

    private fun createVideo() {
        if (rendering.get()) { status.text = "Video zaten oluşturuluyor."; return }
        val errors = MathEngine.validate(scenario.world)
        if (errors.isNotEmpty()) { status.text = "DÜNYA DOĞRULAMADAN GEÇEMEDİ\n${errors.joinToString("\n")}"; return }
        if (!rendering.compareAndSet(false, true)) return
        status.text = "VİDEO OLUŞTURULUYOR\n${scenario.title}\n540×960 • 30 FPS • 300 kare"
        HumanVideoRenderer.render(this, scenario) { uri, error ->
            runOnUiThread {
                rendering.set(false)
                if (error == null && uri != null) {
                    latestUri = uri
                    VideoRecordStore.add(this, VideoRecord(scenario.title, uri, scenario.seed, System.currentTimeMillis()))
                    status.text = "VİDEO KAYDEDİLDİ\n${scenario.title}\n${uri}\nÖnizlemek için Son Videoyu Aç'a dokun."
                } else {
                    status.text = "VİDEO OLUŞTURMA BAŞARISIZ\n${error?.message ?: "Bilinmeyen kodlayıcı hatası"}\nFizik / Kare Testi'ni çalıştır ve cihaz depolamasını kontrol et."
                }
            }
        }
    }

    private fun runPhysicsTest() {
        status.text = "ÜRETİLEN FİZİK DÜNYASI TEST EDİLİYOR…"
        Thread {
            val report = PhysXFrameTest.run()
            runOnUiThread { status.text = "ÜRETİM DÜNYASI TESTİ\n" + report.toDisplayText() }
        }.start()
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

    private fun downloadLatestApk() {
        try {
            val request = DownloadManager.Request(Uri.parse(latestApkUrl))
                .setTitle("MathMotor Video — Güncel APK")
                .setDescription("Güncel uygulama sürümü indiriliyor")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MathMotorVideo.apk")
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            status.text = "APK İNDİRME BAŞLATILDI\nİndirme tamamlanınca bildirimden veya Dosyalar > İndirilenler bölümünden MathMotorVideo.apk dosyasını aç. Android kurulum izni sorarsa izin vermen gerekebilir."
        } catch (e: Exception) {
            status.text = "APK indirilemedi: ${e.message}\nİnternet bağlantısını kontrol et."
        }
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

    private fun updateScenarioText() {
        scenarioText.text = "${scenario.title}\n${scenario.kind.label}  •  seed ${scenario.seed}"
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