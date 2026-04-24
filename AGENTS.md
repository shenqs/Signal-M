# AGENTS.md - SignalMonitor Project Guidelines

## Project Overview

SignalMonitor is an Android application for real-time WiFi & cellular signal strength monitoring, radiation level estimation, and 3D satellite visualization.

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 34
- **JDK**: 17
- **Kotlin Version**: 1.9.20
- **Gradle Plugin**: Android Gradle Plugin 8.2.0

---

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Install debug on connected device
./gradlew installDebug
```

Note: If gradlew is not present, generate it with `gradle wrapper` or use system gradle.

---

## Test Commands

```bash
# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.signalmontor.app.RadiationCalculatorTest"

# Run a single test method
./gradlew test --tests "com.signalmontor.app.RadiationCalculatorTest.testWifiRadiation_StrongSignal"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
```

Test framework: JUnit 4 (`testImplementation("junit:junit:4.13.2")`)

---

## Code Style Guidelines

### Package Structure

```
com.signalmontor.app/
├── MainActivity.kt           # Main Activity
├── NetworkChangeReceiver.kt  # BroadcastReceiver
├── RadiationCalculator.kt    # Singleton calculator
├── RadiationStandard.kt      # Reference data
├── SignalInfo.kt             # Signal data models
├── SatelliteInfo.kt          # Satellite data models
├── SpeedCalculator.kt        # Speed calculation
└── view/                     # Custom Views
    ├── AnimatedSignalBarView.kt
    ├── RadiationGaugeView.kt
    ├── Satellite3DView.kt
    └── SpeedMonitorView.kt
```

### Imports

- Order imports alphabetically
- Group by package hierarchy (Android, AndroidX, Third-party, Local)
- Use wildcard imports only for multiple classes from same package
- Example order:
```kotlin
import android.*
import androidx.*
import com.google.android.*
import org.json.JSONObject
import java.io.*
import com.signalmontor.app.*
```

### Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Classes | PascalCase | `MainActivity`, `RadiationCalculator` |
| Functions | camelCase | `calculateWiFiRadiation()`, `refreshWifi()` |
| Properties | camelCase | `wifiManager`, `currentGpsCount` |
| Constants | UPPER_SNAKE_CASE | `WIFI_FREQ_MHZ`, `WEATHER_CACHE_INTERVAL` |
| Data classes | PascalCase | `SignalInfo`, `SatelliteInfo`, `RadiationInfo` |
| Enums | PascalCase | `SignalType`, `SignalQuality`, `RadiationRisk` |
| Custom Views | PascalCase + `View` suffix | `AnimatedSignalBarView` |

### File Organization

1. Package declaration (no line break after)
2. Imports (one line break after)
3. Class/object declaration
4. Companion object/constants at top of class
5. Public properties/fields
6. Private properties/fields
7. Public methods
8. Private methods
9. Inner/nested classes

### Kotlin Conventions

- Use `object` for singletons: `object RadiationCalculator`
- Use `data class` for immutable models
- Use `enum class` for type definitions with properties
- Use `@JvmOverloads` for custom View constructors
- Prefer `val` over `var` for immutability
- Use `lateinit var` for late-initialized Android components
- Use `?.let {}` for null-safe operations
- Use `when` expressions instead of chains of `if-else`

### Custom View Pattern

```kotlin
class CustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // Paint objects initialized in init block
    // Public setter methods for updating state
    // onDraw() for rendering
    // onSizeChanged() for layout calculations
}
```

### Formatting

- Indent: 4 spaces (no tabs)
- Max line length: 120 characters
- One statement per line
- Opening brace on same line as declaration
- Blank line between logical sections
- No trailing whitespace

### Types & Nullability

- Explicitly declare nullable types with `?`
- Use `NonNull` annotations for Android parameters
- Use `coerceIn()` for value bounds instead of manual checks
- Use `toFloat()` explicit conversions for numeric operations

### Error Handling

- Use `try-catch` for Android API calls that may throw `SecurityException`
- Print stack trace for caught exceptions: `e.printStackTrace()`
- Handle permission denials gracefully with user feedback
- Use `@SuppressLint("MissingPermission")` only when permission check is handled elsewhere

### Android-Specific Patterns

- Use `runOnUiThread {}` for UI updates from background threads
- Use `Handler(Looper.getMainLooper())` for delayed/scheduled operations
- Use `registerForActivityResult()` for permission requests
- Use ViewBinding (enabled in build.gradle.kts)
- Clean up listeners in `onPause()`, `onDestroy()`
- Implement lifecycle-aware auto-refresh with `onResume()`, `onPause()`

---

## Testing Guidelines

### Unit Tests

- Place in `app/src/test/java/com/signalmontor/app/`
- Use JUnit 4: `org.junit.Assert.*`
- Test naming: `test<MethodName>_<Scenario>()`
- Example: `testWifiRadiation_StrongSignal()`

```kotlin
@Test
fun testWifiRadiation_StrongSignal() {
    val result = RadiationCalculator.calculateWiFiRadiation(-40)
    assertNotNull(result)
    assertTrue(result.estimatedSAR >= 0)
    assertTrue(result.percentageOfLimit >= 0 && result.percentageOfLimit <= 100)
}
```

### Assertions to Use

- `assertEquals(expected, actual)` for exact values
- `assertTrue(condition)` for boolean checks
- `assertNotNull(obj)` for null checks
- `assertNotEquals(a, b)` for difference checks

### Instrumented Tests

- Place in `app/src/androidTest/java/com/signalmontor/app/`
- Use AndroidX Test: `androidx.test.ext:junit:1.1.5`
- Use Espresso for UI tests: `androidx.test.espresso:espresso-core:3.5.1`

---

## Dependencies

Key dependencies from `app/build.gradle.kts`:

```kotlin
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.11.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
```

---

## Build Configuration

- ViewBinding enabled: `buildFeatures { viewBinding = true }`
- Kotlin JVM target: 17
- ProGuard enabled for release builds
- Release signing requires `keystore.properties` file

---

## Permissions Required

- `ACCESS_FINE_LOCATION` - WiFi info and GPS
- `ACCESS_COARSE_LOCATION` - Network-assisted location
- `READ_PHONE_STATE` - Cellular signal info
- `ACCESS_WIFI_STATE` - WiFi signal strength
- `ACCESS_NETWORK_STATE` - Network state changes

---

## Notes for Agents

1. Always run `./gradlew test` after making changes to business logic
2. Run `./gradlew lint` before submitting changes
3. Use Chinese strings for user-facing text (app targets Chinese users)
4. Follow existing patterns when adding new features
5. Keep custom Views in `view/` subdirectory
6. Keep data models as top-level classes in main package
7. Do not modify `keystore.properties` or signing configuration