package com.signalmontor.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟真机权限授权场景：
 * 1. 授予全部运行时权限后启动 → 点击“同意并继续” → 权限按钮隐藏、功能启动
 * 2. 拒绝权限场景 → 应用给出引导（权限按钮出现/不崩溃）
 */
@RunWith(AndroidJUnit4::class)
class PermissionFlowTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    )

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun grantedPermissions_hidePermissionPromptAndStartMonitoring() {
        // 权限已授予：点击确认弹窗后应隐藏权限请求按钮，进入监测状态
        UiTestHelper.acceptPermissionDialog()
        UiTestHelper.waitForUiSettle(2000)

        Espresso.onView(withId(R.id.permissionBtn)).check(ViewAssertions.doesNotExist())
        // 自动刷新已启动：信号文本可见
        Espresso.onView(withId(R.id.wifiStrength)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.sensorStatus)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun grantedPermissions_gnssListenerStartsWithoutCrash() {
        UiTestHelper.acceptPermissionDialog()
        UiTestHelper.waitForUiSettle(2000)
        // 卫星卡片（GNSS 监听回调驱动的 UI）应正常渲染
        Espresso.onView(withId(R.id.satelliteCard)).perform(androidx.test.espresso.action.ViewActions.scrollTo())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}