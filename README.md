# Signal Monitor | 信号监测

一款Android应用程序，用于实时监测WiFi和蜂窝网络信号强度、估算手机辐射水平，并提供3D卫星可视化。

An Android app for real-time WiFi & cellular signal strength monitoring, radiation level estimation, and 3D satellite visualization.

---

## 功能特性 | Features

### 信号监测 | Signal Monitoring
- **WiFi**: 实时显示信号强度（dBm）、SSID、BSSID、频率、速率，带动画信号条
- **蜂窝网络**: 支持2G/3G/4G/5G信号强度监测，显示网络类型和基站信息
- **自动刷新**: 每3秒自动更新数据，带平滑动画过渡

### 辐射估算 | Radiation Estimation
- 基于信号强度计算SAR值（比吸收率）
- 颜色编码仪表盘：🟢 安全 → 🟡 注意 → 🟠 警告 → 🔴 危险
- 显示占国际安全限值（ICNIRP/FCC/中国国标）的百分比
- 根据辐射水平提供健康建议

### 3D卫星地球 | 3D Satellite Globe
- 交互式地球模型，带大陆轮廓和经纬线网格
- 实时显示GPS、北斗、GLONASS、Galileo卫星位置
- 彩色卫星标识，显示信噪比和仰角
- 触摸控制：拖拽旋转、双指缩放
- 空闲时自动旋转

### 定位来源识别 | Position Source Identification
- 区分真实卫星信号与辅助定位（WiFi/基站/AGPS）
- 可视化徽章显示当前活跃的定位来源

### 安全标准参考 | Safety Standards Reference
- ICNIRP、FCC、中国GB 21288-2007、IEEE C95.1限值
- 常见设备辐射水平对比
- 环境辐射参考值

---

## 系统要求 | System Requirements

- Android 8.0 (API 26) 及以上
- JDK 17
- Android SDK 34

## 权限说明 | Permissions

| 权限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | 获取WiFi信息和GPS定位 |
| `ACCESS_COARSE_LOCATION` | 网络辅助定位 |
| `READ_PHONE_STATE` | 获取蜂窝网络信号信息 |
| `ACCESS_WIFI_STATE` | 获取WiFi信号强度 |
| `ACCESS_NETWORK_STATE` | 检测网络状态变化 |

## 构建说明 | Build

```bash
# 前置条件: Android SDK 34, JDK 17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug     # 调试版本
./gradlew assembleRelease   # 签名发布版本
```

或使用 Android Studio 打开项目并点击运行。

## 测试 | Testing

```bash
./gradlew test              # 运行单元测试（30个测试用例）
./gradlew connectedAndroidTest  # 运行仪器测试
```

## 技术栈 | Tech Stack

- **语言**: Kotlin
- **最低SDK**: 26 (Android 8.0)
- **目标SDK**: 35 (Android 15)
- **UI**: Material Design 3, 自定义View (Canvas 2D)
- **架构**: 单Activity, View-based UI

## 项目结构 | Project Structure

```
app/src/main/java/com/signalmontor/app/
├── MainActivity.kt              # 主界面和活动逻辑
├── NetworkChangeReceiver.kt     # 网络变化广播接收器
├── RadiationCalculator.kt       # SAR和辐射水平计算
├── RadiationStandard.kt         # 安全标准参考数据
├── SatelliteInfo.kt             # 卫星和定位来源模型
├── SignalInfo.kt                # 信号质量模型
└── view/
    ├── AnimatedSignalBarView.kt # 动画信号强度条
    ├── RadiationGaugeView.kt    # 辐射水平弧形仪表盘
    └── Satellite3DView.kt       # 3D地球卫星视图
```

## 信号强度参考 | Signal Strength Reference

### WiFi信号
| 信号强度 (dBm) | 质量 |
|----------------|------|
| -30 ~ -50 | 极好 |
| -50 ~ -60 | 良好 |
| -60 ~ -70 | 一般 |
| -70 ~ -80 | 较差 |
| < -80 | 无信号 |

### 蜂窝网络信号
| 信号强度 (dBm) | 质量 |
|----------------|------|
| > -70 | 极好 |
| -70 ~ -85 | 良好 |
| -85 ~ -100 | 一般 |
| -100 ~ -115 | 较差 |
| < -115 | 无信号 |

## 免责声明 | Disclaimer

辐射水平估算基于理论模型，实际值可能因设备型号、天线设计和环境而异。本应用仅供参考，不构成医疗或安全合规建议。

Radiation estimates are based on theoretical models and should not be used for medical or safety compliance purposes. Actual SAR values vary by device model, antenna design, and environment.

## 许可证 | License

MIT License
