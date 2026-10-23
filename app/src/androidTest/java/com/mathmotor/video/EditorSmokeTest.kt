package com.mathmotor.video

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke checks for basic editor navigation and its always-available timeline.
 * These checks intentionally do not claim to validate decoding, trimming, or export of a real MP4.
 */
@RunWith(AndroidJUnit4::class)
class EditorSmokeTest {

    private val videoReadPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    @get:Rule
    val videoPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(videoReadPermission)

    @Test
    fun mainScreenOpensEditorAndShowsTimelineTracks() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withText("VİDEO EDİTÖRÜ • KES / YAZI / SES"))
                .perform(scrollTo(), click())

            onView(withText("ZAMAN ÇİZELGESİ")).check(matches(isDisplayed()))
            onView(withText("+ Video")).check(matches(isDisplayed()))
            onView(withText("+ Klip")).check(matches(isDisplayed()))
            onView(withText("+ Metin")).check(matches(isDisplayed()))
            onView(withText("+ Müzik")).check(matches(isDisplayed()))
            onView(withText("+ Ses efekti")).check(matches(isDisplayed()))
            onView(withText("SÜRE")).check(matches(isDisplayed()))
            onView(withText("VIDEO")).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun editorOpensWithoutASelectedSourceVideo() {
        val scenario = ActivityScenario.launch(VideoEditorActivity::class.java)
        try {
            assertVisibleText(scenario, "MathMotor Studio")
            assertVisibleText(scenario, "ZAMAN ÇİZELGESİ")
            // Several editor panels intentionally reuse these labels. Assert presence
            // in the visible view tree instead of requiring a unique Espresso match.
            assertVisibleText(scenario, "METİN")
            assertVisibleText(scenario, "MÜZİK")
            assertVisibleText(scenario, "SES")
        } finally {
            scenario.close()
        }
    }

    private fun assertVisibleText(
        scenario: ActivityScenario<VideoEditorActivity>,
        expected: String
    ) {
        scenario.onActivity { activity ->
            val root = activity.findViewById<View>(android.R.id.content)
            assertTrue(
                "Expected visible text '$expected' in editor.",
                containsVisibleText(root, expected)
            )
        }
    }

    private fun containsVisibleText(view: View, expected: String): Boolean {
        if (view.visibility == View.VISIBLE &&
            view is TextView &&
            view.text?.toString() == expected &&
            view.width > 0 && view.height > 0
        ) return true

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsVisibleText(view.getChildAt(index), expected)) return true
            }
        }
        return false
    }
}
