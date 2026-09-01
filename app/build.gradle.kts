import java.io.FileInputStream
import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val versionFile = rootProject.file("version.properties")
val versionProps = Properties()
var majorVersion = 20
var minorVersion = 0
var patchVersion = 0

if (versionFile.exists()) {
    versionProps.load(FileInputStream(versionFile))
    majorVersion = (versionProps["major"] as String).toInt()
    minorVersion = (versionProps["minor"] as String).toInt()
    patchVersion = (versionProps["patch"] as String).toInt()
}

minorVersion += 1
versionProps["major"] = majorVersion.toString()
versionProps["minor"] = minorVersion.toString()
versionProps["patch"] = patchVersion.toString()
versionFile.outputStream().use { versionProps.store(it, null) }

android {
    namespace = "com.signalmontor.app"
    // 保持 34：aarch64(Termux/PRoot) 上无官方 arm64 aapt2，Debian aapt2 仅支持 android-34 资源格式
    compileSdk = 34

    defaultConfig {
        applicationId = "com.signalmontor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = majorVersion * 10000 + minorVersion * 100 + patchVersion
        versionName = "$majorVersion.$minorVersion.$patchVersion"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        // compileSdk(34) < targetSdk(35)：aarch64 无 arm64 官方 aapt2 可用，属平台限制
        disable += "GradleCompatible"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
