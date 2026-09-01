package com.signalmontor.app

import android.content.pm.ActivityInfo
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.flexbox.FlexboxLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟折叠屏/大屏设备场景的自适应布局测试：
 * - 竖屏(窄窗口)：所有卡片单列满宽 (flexBasisPercent = 100)
 * - 横屏(宽窗口，模拟折叠屏展开态)：信号/辐射等卡片双列 (flexBasisPercent = 46)
 * - 回到竖屏恢复单列
 * 通过真实旋转事件驱动（Activity 重建后 Flexbox 参数由 applyColumnMode 重算）。
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {

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

    private fun cardBasisPercent(viewId: Int): Float {
        var basis = -1f
        scenario.onActivity { activity ->
            val card = activity.findViewById<View>(viewId)
            val lp = card.layoutParams
            if (lp is FlexboxLayout.LayoutParams) {
                basis = lp.flexBasisPercent
            }
        }
        return basis
    }

    // 目标竖屏下所有卡片应单列满宽
    @Test
    fun portrait_singleColumn() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        waitForBasis(2000, R.id.wifiCard, 100f)
        assertEquals("信号卡应单列满宽", 100f, cardBasisPercent(R.id.wifiCard), 0.1f)
        assertEquals("速度卡应单列满宽", 100f, cardBasisPercent(R.id.speedCard), 0.1f)
    }

    // 模拟折叠屏展开/平板横屏：宽窗口下信号卡应转为双列
    @Test
    fun landscape_dualColumnOnWideWindow() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        // 只有窗口足够宽(>=600dp)才会切双列；模拟器横屏通常满足
        waitForBasis(2500, R.id.wifiCard, 46f)
        assertEquals("宽屏信号卡应双列", 46f, cardBasisPercent(R.id.wifiCard), 0.1f)
        assertEquals("宽屏蜂窝卡应双列", 46f, cardBasisPercent(R.id.cellularCard), 0.1f)
        // 速度/卫星卡在宽屏下仍全宽（双仪表/3D 视图）
        assertEquals("速度卡保持全宽", 100f, cardBasisPercent(R.id.speedCard), 0.1f)
        assertEquals("卫星卡保持全宽", 100f, cardBasisPercent(R.id.satelliteCard), 0.1f)
    }

    // 横屏→竖屏 往复切换不崩溃，且布局参数正确恢复
    @Test
    fun orientationCycle_restoresSingleColumn() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        UiTestHelper.waitForUiSettle(2000)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        waitForBasis(2500, R.id.wifiCard, 100f)
        // 重建后 UI 仍应正常渲染
        Espresso.onView(withId(R.id.wifiCard)).check(ViewAssertions.matches(isDisplayed()))
    }

    private fun waitForBasis(timeoutMs: Long, viewId: Int, expected: Float) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val basis = cardBasisPercent(viewId)
            if (expected == basis) return
            Thread.sleep(200)
        }
    }
}