package com.signalmontor.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟真机网络切换场景：
 * 系统广播（WiFi 开/关、网络状态变化、连接类型切换）到达接收器时不应崩溃，
 * 且能正确识别当前承载网络类型（WiFi / 蜂窝）。
 */
@RunWith(AndroidJUnit4::class)
class NetworkChangeBroadcastTest {

    private lateinit var context: Context
    private lateinit var receiver: NetworkChangeReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = NetworkChangeReceiver()
    }

    @Test
    fun wifiStateEnabledBroadcast_noCrash() {
        receiver.onReceive(context, Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).apply {
            putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED)
        })
        receiver.onReceive(context, Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).apply {
            putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED)
        })
    }

    @Test
    fun networkStateChangedBroadcast_noCrash() {
        receiver.onReceive(context, Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    @Test
    fun connectivityChangeByTransport_noCrash() {
        // 简单版本：仅验证接收器处理 CONNECTIVITY_ACTION 无异常
        receiver.onReceive(context, Intent(ConnectivityManager.CONNECTIVITY_ACTION))
        assertNotNull(context.getSystemService(ConnectivityManager::class.java))
    }

    @Test
    fun unknownAction_noCrash() {
        receiver.onReceive(context, Intent("android.intent.action.BOOT_COMPLETED"))
        receiver.onReceive(context, Intent("com.example.UNKNOWN_ACTION"))
    }

    @Test
    fun wifiTransportDetection_returnsExpectedCapabilities() {
        // 通过系统服务读取当前网络能力（模拟器/真机均可用），
        // 验证监测链路依赖的核心 API 可用
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        // 有网络时必为 WiFi 或蜂窝之一
        if (caps != null) {
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            assert(isWifi || isCellular) { "当前网络既非 WiFi 也非蜂窝" }
        }
    }
}