package com.signalmontor.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器上的速度引擎集成测试：
 * - SpeedCalculator 在真实设备 JVM 上的完整链路（GPS 注入 → 平滑 → UI 消费）
 * - 主界面速度仪表与传感器状态区随刷新周期更新
 */
@RunWith(AndroidJUnit4::class)
class SpeedUiIntegrationTest {

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

    // 设备端 SpeedCalculator 全链路：GPS 数据驱动 → 平滑收敛 → 状态合理
    @Test
    fun speedEngine_gpsDrivenPipelineOnDevice() {
        val calc = SpeedCalculator()
        repeat(60) {
            calc.updateGpsSpeed(8.33f, 8f)   // 30 km/h
            calc.updateLocationSpeed(8.33f)
            calc.getSpeed()
        }
        val data = calc.getSpeed()
        assertTrue("应收敛到 30km/h 附近, 实际 ${data.speed}", data.speed in 20f..40f)
        assertEquals("步频应为 0(无步态)", 0f, data.stepFrequency, 0.01f)
        assertTrue("高精度 GPS 置信度应较高", data.confidence >= 0.6f)
    }

    // 静止噪声防护：低精度尖峰不应导致速度跃升（真机上的真实 GPS 毛刺场景）
    @Test
    fun speedEngine_stationaryRejectsGpsGlitchOnDevice() {
        val calc = SpeedCalculator()
        repeat(40) {
            calc.updateGpsSpeed(0f, 25f)
            calc.getSpeed()
        }
        calc.updateGpsSpeed(12f, 60f)   // 43km/h 但精度差
        calc.updateLocationSpeed(12f)
        val data = calc.getSpeed()
        assertTrue("静止时低速毛刺不应跃升, 实际 ${data.speed}", data.speed < 25f)
    }

    // UI 集成：点击刷新按钮后速度仪表与传感器状态区渲染正常
    @Test
    fun ui_refreshDrivesSpeedMonitorAndSensorStatus() {
        Espresso.onView(withId(R.id.refreshBtn)).perform(ViewActions.click())
        UiTestHelper.waitForUiSettle(1500)
        Espresso.onView(withId(R.id.speedMonitorView)).check(ViewAssertions.matches(isDisplayed()))
        Espresso.onView(withId(R.id.sensorStatus)).check(ViewAssertions.matches(isDisplayed()))
    }

    // UI 集成：自动刷新周期内传感器状态区保持更新（不崩溃、内容存在）
    @Test
    fun ui_autoRefreshUpdatesSensorStatus() {
        UiTestHelper.waitForUiSettle(7000)
        Espresso.onView(withId(R.id.sensorStatus)).check(ViewAssertions.matches(withText(containsString("传感器"))))
        Espresso.onView(withId(R.id.speedMonitorView)).check(ViewAssertions.matches(isDisplayed()))
    }
}