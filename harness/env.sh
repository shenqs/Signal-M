#!/usr/bin/env bash
# env.sh — 开发环境就位与版本约束校验
# 安装 JDK17、Android SDK 34、Gradle 8.9，生成 gradle wrapper 与 local.properties。
# 幂等：已满足约束则跳过，可重复执行。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mirror.sh"

step "环境就位与版本约束校验"

# 装包前先配置国内镜像（apt + Maven + Gradle 发行版）
setup_all_mirrors

# ---------------------------------------------------------------------------
# 1. 操作系统与包管理器
# ---------------------------------------------------------------------------
PM="$(pkg_mgr)"
[[ "$PM" != none ]] || die "未识别的包管理器，无法自动安装依赖"
info "包管理器: $PM"

install_pkgs() {
    # PRoot/Termux 内通常已是 root 且无 sudo；仅非 root 时才提权
    local SUDO=""
    if [[ "$(id -u)" != 0 ]] && command -v sudo >/dev/null 2>&1; then
        SUDO=(sudo)
    else
        SUDO=()
    fi
    case "$PM" in
        apt)
            export DEBIAN_FRONTEND=noninteractive
            "${SUDO[@]}" apt-get update -qq
            "${SUDO[@]}" apt-get install -y --no-install-recommends "$@"
            ;;
        dnf) "${SUDO[@]}" dnf install -y "$@" ;;
        pacman) "${SUDO[@]}" pacman -S --noconfirm "$@" ;;
        apk) "${SUDO[@]}" apk add --no-cache "$@" ;;
    esac
}

# ---------------------------------------------------------------------------
# 2. JDK 17
# ---------------------------------------------------------------------------
java_version_ok() {
    command -v java >/dev/null 2>&1 || return 1
    local v
    v=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\..*/\1/')
    [[ "$v" == "$REQUIRED_JDK_MAJOR" ]]
}

force_jdk17_default() {
    # 多 JDK 并存时（如默认指针被切到 25），强制 java/javac 回到 17
    local jdk17=/usr/lib/jvm/java-17-openjdk-arm64
    if [[ -x "$jdk17/bin/java" ]]; then
        warn "检测到默认 java 非 17，强制切换 alternatives 指向 $jdk17"
        update-alternatives --set java "$jdk17/bin/java" || true
        update-alternatives --set javac "$jdk17/bin/javac" || true
    fi
}

if java_version_ok; then
    log "JDK $REQUIRED_JDK_MAJOR 已就绪: $(java -version 2>&1 | head -1)"
else
    warn "缺少 JDK $REQUIRED_JDK_MAJOR，开始安装"
    case "$PM" in
        apt) install_pkgs openjdk-17-jdk-headless unzip wget ;;
        *) install_pkgs jdk17-openjdk unzip wget ;;
    esac
    # 装了 17 但默认指针指向其他版本时，强制切回（避免交替者漂移）
    force_jdk17_default
    java_version_ok || die "JDK 17 安装后校验失败（可用 update-alternatives 手动切换）"
fi
if java_version_ok; then
    :
else
    force_jdk17_default
    java_version_ok || warn "默认 java 指向非 17，将尝试用 JDK 17 路径作为 JAVA_HOME"
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
    # 取 java 实际二进制所在 JDK 目录
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    # 若解析到的是非 17 JDK，且 17 存在，则用 17
    if [[ "$JAVA_HOME" != *17* && -d /usr/lib/jvm/java-17-openjdk-arm64 ]]; then
        JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
    fi
fi
export JAVA_HOME
log "JAVA_HOME=$JAVA_HOME"

# ---------------------------------------------------------------------------
# 3. Android SDK 34
# ---------------------------------------------------------------------------
# gradle.properties 中已硬编码 Debian aapt2 路径，故优先使用 apt 安装的 SDK。

# 兜底安装器：从 dl.google.com 拉 cmdline-tools，再用 sdkmanager 装 platform/build-tools/platform-tools。
install_sdk_via_sdkmanager() {
    ensure_dir "$ANDROID_HOME/cmdline-tools"
    local cdt_url="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    local zip="$ANDROID_HOME/cmdline-tools/cmdline-tools.zip"
    if [[ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]]; then
        if [[ ! -f "$zip" ]]; then
            log "下载 Android cmdline-tools..."
            wget -q -O "$zip" "$cdt_url" || die "下载 cmdline-tools 失败"
        else
            info "复用已下载的 cmdline-tools.zip"
        fi
        unzip -q -o "$zip" -d "$ANDROID_HOME/cmdline-tools"
        # 规范目录名为 latest
        mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" 2>/dev/null || true
    fi
    local sdm="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
    [[ -x "$sdm" ]] || die "sdkmanager 未就位: $sdm"
    yes | "$sdm" --sdk_root="$ANDROID_HOME" \
        "platforms;android-$REQUIRED_COMPILE_SDK" \
        "build-tools;$REQUIRED_BUILD_TOOLS" \
        "platform-tools" >/dev/null 2>&1 || true
}

sdk_ok() {
    [[ -d "$ANDROID_HOME/platforms/android-$REQUIRED_COMPILE_SDK" ]] && \
    [[ -d "$ANDROID_HOME/build-tools/$REQUIRED_BUILD_TOOLS" ]]
}

if sdk_ok; then
    log "Android SDK (platform-$REQUIRED_COMPILE_SDK, build-tools $REQUIRED_BUILD_TOOLS) 已就绪"
    log "  ANDROID_HOME=$ANDROID_HOME"
else
    warn "Android SDK 不完整，开始安装"
    # Debian/Ubuntu apt 路线：base SDK + Debian aapt2（gradle.properties 引用）+ Google platform-34
    # 包名因发行版而异，逐个尝试并容错；缺失组件随后由 sdkmanager 兜底。
    if [[ "$PM" == apt ]]; then
        for pkg in android-sdk android-sdk-build-tools \
                   google-android-platform-${REQUIRED_COMPILE_SDK}-installer \
                   android-sdk-platform-tools; do
            install_pkgs "$pkg" 2>/dev/null || warn "apt 包 $pkg 跳过（可能不可用）"
        done
    else
        install_pkgs android-sdk 2>/dev/null || true
    fi
    # 任何缺失组件（典型为 build-tools/34.0.0）由 sdkmanager 兜底
    if ! sdk_ok; then
        warn "apt 路线未完整，使用 sdkmanager 补齐"
        install_sdk_via_sdkmanager
    fi
    sdk_ok || die "Android SDK 安装后仍不完整，请手动检查 $ANDROID_HOME"
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# 写入 local.properties（sdk.dir），Gradle 读取它定位 SDK
if ! grep -q "^sdk.dir=" "$LOCAL_PROPS" 2>/dev/null; then
    echo "sdk.dir=$ANDROID_HOME" > "$LOCAL_PROPS"
    log "已写入 $LOCAL_PROPS"
else
    log "local.properties 已存在，跳过"
fi

# ---------------------------------------------------------------------------
# 4. Gradle 8.9 + wrapper
# ---------------------------------------------------------------------------
# .gitignore 已锁定 gradle-8.9；AGP 8.2.0 要求 Gradle ≥8.2，8.9 兼容。
gradle_bin_ok() {
    command -v gradle >/dev/null 2>&1 && \
    [[ "$(gradle --version 2>/dev/null | awk '/Gradle /{print $2}')" == "$REQUIRED_GRADLE" ]]
}

wrapper_ok() {
    [[ -x "$REPO_ROOT/gradlew" ]] && \
    grep -q "gradleVersion = \"$REQUIRED_GRADLE\"" "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null
}

if wrapper_ok; then
    log "gradlew ($REQUIRED_GRADLE) 已就绪"
else
    if ! gradle_bin_ok; then
        warn "安装 Gradle $REQUIRED_GRADLE (二进制，腾讯云镜像)"
        gdir="$REPO_ROOT/.gradle-dist"
        ensure_dir "$gdir"
        gurl="$(gradle_dist_url "$REQUIRED_GRADLE")"
        gpath="$ANDROID_HOME"
        if [[ ! -x "$gpath/gradle-$REQUIRED_GRADLE/bin/gradle" ]]; then
            # 主镜像失败则回退官方源
            if ! wget -q -O "$gdir/gradle.zip" "$gurl"; then
                warn "镜像下载失败，回退官方源: services.gradle.org"
                wget -q -O "$gdir/gradle.zip" \
                    "https://services.gradle.org/distributions/gradle-$REQUIRED_GRADLE-bin.zip" \
                    || die "下载 Gradle 失败"
            fi
            unzip -q -o "$gdir/gradle.zip" -d "$gpath" || die "解压 Gradle 失败"
            rm -f "$gdir/gradle.zip"
        fi
        export PATH="$gpath/gradle-$REQUIRED_GRADLE/bin:$PATH"
        command -v gradle >/dev/null 2>&1 || die "gradle 仍不可用"
    fi
    log "生成 gradle wrapper (Gradle $REQUIRED_GRADLE)"
    ( cd "$REPO_ROOT" && gradle wrapper --gradle-version "$REQUIRED_GRADLE" --distribution-type bin ) \
        || die "gradle wrapper 生成失败"
    # 修补 wrapper distributionUrl 指向腾讯云镜像（gradlew 首次运行时加速）
    patch_gradle_wrapper_mirror
fi

# ---------------------------------------------------------------------------
# 5. 终态自检
# ---------------------------------------------------------------------------
step "环境自检"
{
    echo "JDK       : $(java -version 2>&1 | head -1)"
    echo "JAVA_HOME : $JAVA_HOME"
    echo "Gradle    : $($REPO_ROOT/gradlew --version 2>/dev/null | awk '/Gradle /{print $2}')"
    echo "AGP       : $REQUIRED_AGP (project)"
    echo "Kotlin    : $REQUIRED_KOTLIN (project)"
    echo "SDK root  : $ANDROID_HOME"
    echo "Platform  : $(ls "$ANDROID_HOME/platforms" 2>/dev/null | tr '\n' ' ')"
    echo "Build-Tools: $(ls "$ANDROID_HOME/build-tools" 2>/dev/null | tr '\n' ' ')"
    echo "Wrapper   : $([ -x "$REPO_ROOT/gradlew" ] && echo OK || echo MISSING)"
    echo "local.properties : $([ -f "$LOCAL_PROPS" ] && echo OK || echo MISSING)"
} | tee "$LOG_DIR/env-check.txt"

log "环境就位完成"
