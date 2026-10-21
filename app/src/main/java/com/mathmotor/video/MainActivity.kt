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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        root.addView(label("LOCAL STUDIO  •  9:16  •  10 SECONDS", 12f, Color.rgb(173, 191, 204), true))
        root.addView(gap(18))
        root.addView(label("CURRENT SCENARIO", 12f, Color.rgb(121, 202, 218), true))
        scenarioText = label("", 18f, Color.WHITE, true)
        root.addView(scenarioText)
        root.addView(label("The physical timeline is generated first. The visual director reads it without changing its states.", 14f, Color.rgb(198, 211, 218), false))
        root.addView(gap(18))
        root.addView(actionButton("NEW WORLD / NEW SCENARIO") { createNewScenario() })
        root.addView(actionButton("GENERATE 10-SECOND VIDEO") { createVideo() })
        root.addView(actionButton("PHYSX / FRAME TEST  •  300 FRAMES") { runPhysicsTest() })
        root.addView(actionButton("OPEN LATEST VIDEO") { openLatest() })
        root.addView(actionButton("VIDEO HISTORY") { showHistory() })
        root.addView(gap(18))
        root.addView(label("STATUS / TEST LOG", 12f, Color.rgb(121, 202, 218), true))
        status = TextView(this).apply {
            setTextColor(Color.rgb(225, 233, 237)); textSize = 13f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(27, 48, 64))
            text = "Ready. Generate a scenario or run the 300-frame physics test."
        }
        root.addView(status, matchWrap().apply { topMargin = dp(8) })
        root.addView(gap(18))
        root.addView(label("RESTORATION BUILD", 12f, Color.rgb(121, 202, 218), true))
        root.addView(label("This source rebuild restores the offline generation, rendering, history and frame-test foundation. The original V7 source archive was not found; external social-data connectors from V7 are not yet reconnected.", 12f, Color.rgb(173, 191, 204), false))
        setContentView(scroll)
        updateScenarioText()
    }

    private fun createNewScenario() {
        scenario = ScenarioEngine.generate(System.currentTimeMillis())
        updateScenarioText()
        status.text = "WORLD GENERATED\nseed=${scenario.seed}\nframes=${scenario.world.frameCount}\nkind=${scenario.kind.label}\nPHYSX=${if (MathEngine.validate(scenario.world).isEmpty()) "VALID" else "INVALID"}"
    }

    private fun createVideo() {
        if (rendering.get()) { status.text = "Video is already rendering."; return }
        val errors = MathEngine.validate(scenario.world)
        if (errors.isNotEmpty()) { status.text = "WORLD REJECTED\n${errors.joinToString("\n")}"; return }
        if (!rendering.compareAndSet(false, true)) return
        status.text = "RENDERING\n${scenario.title}\n540×960 • 30 FPS • 300 frames\nThe screen may remain usable while the video renders."
        HumanVideoRenderer.render(this, scenario) { uri, error ->
            runOnUiThread {
                rendering.set(false)
                if (error == null && uri != null) {
                    latestUri = uri
                    VideoRecordStore.add(this, VideoRecord(scenario.title, uri, scenario.seed, System.currentTimeMillis()))
                    status.text = "VIDEO SAVED\n${scenario.title}\n${uri}\nOpen Latest Video to preview it."
                } else {
                    status.text = "RENDER FAILED\n${error?.message ?: "Unknown encoder error"}\nTry PHYSX / FRAME TEST and check device storage/encoder support."
                }
            }
        }
    }

    private fun runPhysicsTest() {
        status.text = "RUNNING PHYSX / FRAME TEST…"
        Thread {
            val report = PhysXFrameTest.run()
            runOnUiThread { status.text = report.toDisplayText() }
        }.start()
    }

    private fun openLatest() {
        val uri = latestUri ?: VideoRecordStore.list(this).firstOrNull()?.uri
        if (uri.isNullOrBlank()) {
            status.text = "No rendered video yet. Generate a video first."
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { status.text = "Could not open video: ${it.message}" }
    }

    private fun showHistory() {
        val records = VideoRecordStore.list(this)
        if (records.isEmpty()) { status.text = "VIDEO HISTORY\nNo saved videos yet."; return }
        status.text = buildString {
            appendLine("VIDEO HISTORY • ${records.size} saved")
            records.take(20).forEachIndexed { i, record ->
                appendLine("${i + 1}. ${record.title}")
                appendLine("   seed=${record.seed} • ${DateFormat.getDateTimeInstance().format(Date(record.createdAt))}")
            }
            appendLine("\nOPEN LATEST VIDEO opens the most recent record.")
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
