package com.signalmontor.app

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText

/**
 * 仪器测试共享工具：
 * 模拟真机场景 —— 处理首次启动的权限确认弹窗、滚动、长等待等。
 */
object UiTestHelper {

    /** 点击系统权限说明弹窗的“拒绝”，保证 UI 测试不被模态弹窗阻塞 */
    fun dismissPermissionDialog() {
        try {
            Espresso.onView(withText("拒绝")).perform(ViewActions.click())
        } catch (_: Throwable) {
            // 弹窗可能已被系统自动化处理或未弹出，忽略
        }
    }

    /** 点击“同意并继续”（权限授予流程的起始按钮） */
    fun acceptPermissionDialog() {
        Espresso.onView(withText("同意并继续")).perform(ViewActions.click())
    }

    /** 滚动到目标 View 后再断言（模拟手指滚动查看下方卡片） */
    fun scrollToAndCheck(viewId: Int, assertion: ViewAssertion = ViewAssertions.matches(isDisplayed())) {
        Espresso.onView(withId(viewId)).perform(ViewActions.scrollTo()).check(assertion)
    }

    /** 等待自动刷新周期，让异步 UI 更新稳定（模拟真实使用中的等待） */
    fun waitForUiSettle(millis: Long = 3500) {
        Thread.sleep(millis)
        Espresso.onIdle()
    }
}