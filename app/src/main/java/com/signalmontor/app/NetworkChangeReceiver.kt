package com.signalmontor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        when (action) {
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                when (wifiState) {
                    WifiManager.WIFI_STATE_ENABLED -> {
                        // WiFi已启用
                    }
                    WifiManager.WIFI_STATE_DISABLED -> {
                        // WiFi已禁用
                    }
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                // WiFi网络状态变化
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)

                when {
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                        // 当前使用WiFi
                    }
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                        // 当前使用蜂窝网络
                    }
                }
            }
        }
    }
}
