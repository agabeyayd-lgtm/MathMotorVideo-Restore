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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke checks for basic editor navigation and its always-available timeline.
 * These checks intentionally do not claim to validate decoding, trimming, or export of a real MP4.
 */
@RunWith(AndroidJUnit4::class)
class EditorSmokeTest {

    @get:Rule
    val videoPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_VIDEO)

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
            onView(withText("MathMotor Studio")).check(matches(isDisplayed()))
            onView(withText("ZAMAN ÇİZELGESİ")).check(matches(isDisplayed()))
            onView(withText("METİN")).check(matches(isDisplayed()))
            onView(withText("MÜZİK")).check(matches(isDisplayed()))
            onView(withText("SES")).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }
}
