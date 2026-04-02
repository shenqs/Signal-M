# Signal Monitor

Android app for real-time WiFi & cellular signal strength monitoring, radiation level estimation, and 3D satellite visualization.

## Features

### Signal Monitoring
- **WiFi**: RSSI, SSID, BSSID, frequency, link speed with animated signal bars
- **Cellular**: 2G/3G/4G/5G signal strength (dBm), network type, PCI/CI info
- **Auto-refresh**: Data updates every 3 seconds with smooth animations

### Radiation Estimation
- SAR (Specific Absorption Rate) calculation based on signal strength
- Color-coded gauge: 🟢 Safe → 🟡 Notice → 🟠 Warning → 🔴 Danger
- Percentage of international safety limits (ICNIRP/FCC/China GB)
- Health recommendations based on exposure level

### 3D Satellite Globe
- Interactive Earth globe with continent outlines and grid lines
- Real-time satellite positions for GPS, Beidou, GLONASS, Galileo
- Color-coded satellites with SNR and elevation labels
- Touch controls: drag to rotate, pinch to zoom
- Auto-rotation when idle

### Position Source Identification
- Distinguishes real GNSS signals from assisted positioning (WiFi/cell/AGPS)
- Visual badges showing active positioning sources

### Safety Standards Reference
- ICNIRP, FCC, China GB 21288-2007, IEEE C95.1 limits
- Common device radiation levels comparison
- Environmental radiation reference

## Screenshots

| Signal Cards | 3D Globe | Radiation Gauge |
|-------------|----------|-----------------|
| WiFi & cellular signal strength with animated bars | Interactive Earth with satellite positions | Color-coded SAR level gauge |

## Build

```bash
# Prerequisites: Android SDK 34, JDK 17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug     # Debug APK
./gradlew assembleRelease   # Signed release APK
```

Or open in Android Studio and click Run.

## Permissions

| Permission | Purpose |
|-----------|---------|
| `ACCESS_FINE_LOCATION` | WiFi info & GPS positioning |
| `ACCESS_COARSE_LOCATION` | Network-based location |
| `READ_PHONE_STATE` | Cellular signal info |
| `ACCESS_WIFI_STATE` | WiFi signal strength |
| `ACCESS_NETWORK_STATE` | Network connectivity |

## Testing

```bash
./gradlew test              # Run unit tests (30 tests)
./gradlew connectedAndroidTest  # Run instrumented tests
```

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **UI**: Material Design 3, Custom Views (Canvas 2D)
- **Architecture**: Single Activity, View-based UI

## Project Structure

```
app/src/main/java/com/signalmontor/app/
├── MainActivity.kt              # Main activity with all UI logic
├── NetworkChangeReceiver.kt     # Network state broadcast receiver
├── RadiationCalculator.kt       # SAR & radiation level calculation
├── RadiationStandard.kt         # Safety standards reference data
├── SatelliteInfo.kt             # Satellite & position source models
├── SignalInfo.kt                # Signal quality models
└── view/
    ├── AnimatedSignalBarView.kt # Animated signal strength bars
    ├── RadiationGaugeView.kt    # Arc gauge for radiation level
    └── Satellite3DView.kt       # 3D Earth globe with satellites
```

## Disclaimer

Radiation estimates are based on theoretical models and should not be used for medical or safety compliance purposes. Actual SAR values vary by device model, antenna design, and environment.

## License

MIT License
