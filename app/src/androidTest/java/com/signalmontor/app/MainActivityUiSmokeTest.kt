package com.signalmontor.app

import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟真机首次启动场景的主界面 UI 冒烟测试：
 * - 启动后核心仪表卡片渲染
 * - 下拉刷新 / 按钮刷新不崩溃
 * - 自动刷新周期内 UI 保持稳定
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiSmokeTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestHelper.dismissPermissionDialog()
        UiTestHelper.waitForUiSettle(1500)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun launch_rendersCoreCards() {
        // 顶部信号卡片必须在首屏直接可见
        Espresso.onView(withId(R.id.wifiCard)).check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.cellularCard)).check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.refreshBtn)).check(ViewAssertions.matches(isDisplayed()))
    }

    @Test
    fun launch_rendersSpeedAndSatelliteCardsAfterScroll() {
        // 模拟用户向下滚动查看全部仪表
        Espresso.onView(withId(R.id.speedCard)).perform(ViewActions.scrollTo())
            .check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.satelliteCard)).perform(ViewActions.scrollTo())
            .check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.overallCard)).perform(ViewActions.scrollTo())
            .check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.standardsCard)).perform(ViewActions.scrollTo())
            .check(ViewAssertions.matches(isDisplayed()))
    }

    @Test
    fun refreshButton_triggersRefreshWithoutCrash() {
        Espresso.onView(withId(R.id.refreshBtn)).perform(ViewActions.click())
        UiTestHelper.waitForUiSettle(1500)
        // 刷新后主界面仍应正常渲染（信号值文本存在）
        Espresso.onView(withId(R.id.wifiStrength)).check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.cellularStrength)).check(ViewAssertions.matches(isDisplayed()))
    }

    @Test
    fun autoRefresh_uiStaysStableOverRefreshCycles() {
        // 默认 3s 自动刷新：等待两个周期后界面仍可用
        UiTestHelper.waitForUiSettle(7000)
        Espresso.onView(withId(R.id.wifiCard)).check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.speedMonitorView)).check(ViewAssertions.matches(isDisplayed()))
    }
}