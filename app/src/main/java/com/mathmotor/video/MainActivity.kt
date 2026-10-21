package com.mathmotor.video

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
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

    override fun onCreate(kayıtInstanceState: Bundle?) {
        super.onCreate(kayıtInstanceState)
        window.statusBarColor = Color.rgb(15, 31, 45)
        window.navigationBarColor = Color.rgb(15, 31, 45)
        if (Build.VERSION.SDK_INT in 26..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }
        buildUi()
        val last = VideoRecordStore.list(this).firstOrNull()
        latestUri = last?.uri
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
        root.addView(actionButton("SON VİDEOYU AÇ") { openLatest() })
        root.addView(actionButton("VİDEO GEÇMİŞİ") { showHistory() })
        root.addView(gap(18))
        root.addView(label("DURUM / TEST KAYDI", 12f, Color.rgb(121, 202, 218), true))
        status = TextView(this).apply {
            setTextColor(Color.rgb(225, 233, 237)); textSize = 13f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(27, 48, 64))
            text = "Hazır. Bir senaryo oluştur veya 300 karelik fizik testini çalıştır."
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
        status.text = "DÜNYA OLUŞTURULDU\nseed=${scenario.seed}\nframes=${scenario.world.frameCount}\nkind=${scenario.kind.label}\nPHYSX=${if (MathEngine.validate(scenario.world).isEmpty()) "GEÇERLİ" else "INGEÇERLİ"}"
    }

    private fun createVideo() {
        if (rendering.get()) { status.text = "Video zaten oluşturuluyor."; return }
        val errors = MathEngine.validate(scenario.world)
        if (errors.isNotEmpty()) { status.text = "DÜNYA DOĞRULAMADAN GEÇEMEDİ\n${errors.joinToString("\n")}"; return }
        if (!rendering.compareAndSet(false, true)) return
        status.text = "VİDEO OLUŞTURULUYOR\n${scenario.title}\n540×960 • 30 FPS • 300 frames\nVideo oluşturulurken ekran kullanılabilir durumda kalabilir."
        HumanVideoRenderer.render(this, scenario) { uri, error ->
            runOnUiThread {
                rendering.set(false)
                if (error == null && uri != null) {
                    latestUri = uri
                    VideoRecordStore.add(this, VideoRecord(scenario.title, uri, scenario.seed, System.currentTimeMillis()))
                    status.text = "VİDEO KAYDEDİLDİ\n${scenario.title}\n${uri}\nÖnizlemek için Son Videoyu Aç'a dokun."
                } else {
                    status.text = "VİDEO OLUŞTURMA BAŞARISIZ\n${error?.message ?: "Bilinmeyen kodlayıcı hatası"}\nFİZİK / KARE TESTİ'ni çalıştır; cihaz depolamasını ve kodlayıcı desteğini kontrol et."
                }
            }
        }
    }

    private fun runPhysicsTest() {
        status.text = "FİZİK / KARE TESTİ ÇALIŞIYOR…"
        Thread {
            val report = PhysXFrameTest.run()
            runOnUiThread { status.text = report.toDisplayText() }
        }.start()
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
            appendLine("\nSON VİDEOYU AÇ opens the most recent record.")
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
        text = value; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(35, 95, 117)); gravity = Gravity.CENTER
        setOnClickListener { action() }
        layoutParams = matchWrap().apply { topMargin = dp(7); bottomMargin = dp(3) }
    }

    private fun gap(size: Int) = TextView(this).apply { height = dp(size) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
