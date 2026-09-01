#!/usr/bin/env bash
# config.sh — Signal-M harness 集中配置
# 所有 harness 脚本均 `source` 本文件以获取路径、版本、端口等参数。
# 修改本文件即可调整整套 harness 行为，避免散落硬编码。

# ---------------------------------------------------------------------------
# 路径
# ---------------------------------------------------------------------------
# harness/ 的上一级即仓库根
HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HARNESS_DIR/.." && pwd)"

APP_DIR="$REPO_ROOT/app"
DIST_DIR="$REPO_ROOT/dist"                 # 构建产物归集目录（被 .gitignore 忽略）
LOG_DIR="$HARNESS_DIR/logs"
LOCAL_PROPS="$REPO_ROOT/local.properties"

# 宿主 Android 共享存储（Termux 挂载点；PRoot 中通过 termux-open/am 调用宿主）
HOST_DOWNLOAD_DIR="${HOST_DOWNLOAD_DIR:-/sdcard/Download}"

# ---------------------------------------------------------------------------
# Git
# ---------------------------------------------------------------------------
GIT_REMOTE="${GIT_REMOTE:-origin}"
GIT_BRANCH="${GIT_BRANCH:-main}"

# ---------------------------------------------------------------------------
# 版本约束（与 app/build.gradle.kts、gradle.properties 对齐，禁止随意改动）
# ---------------------------------------------------------------------------
REQUIRED_JDK_MAJOR=17          # compileOptions JavaVersion.VERSION_17
REQUIRED_KOTLIN=1.9.20         # build.gradle.kts (root)
REQUIRED_AGP=8.2.0             # build.gradle.kts (root)
REQUIRED_GRADLE=8.9            # .gitignore 已锁定 8.9；AGP 8.2.0 要求 ≥8.2
REQUIRED_COMPILE_SDK=34        # android { compileSdk = 34 }（aarch64 无 arm64 官方 aapt2；lint 已豁免 GradleCompatible）
REQUIRED_BUILD_TOOLS=34.0.0    # 与 AGP 8.2 对齐
REQUIRED_MIN_SDK=26            # minSdk
REQUIRED_TARGET_SDK=35         # targetSdk

# Android SDK 根目录优先级：环境变量 > Debian 标准路径 > 自管路径
DEFAULT_ANDROID_HOME=/usr/lib/android-sdk
ANDROID_HOME="${ANDROID_HOME:-$DEFAULT_ANDROID_HOME}"
ANDROID_SDK_ROOT="$ANDROID_HOME"

# ---------------------------------------------------------------------------
# HTTP 分发
# ---------------------------------------------------------------------------
# 固定端口 8888（与 AGENTS.md 约定一致；与 app 内 ApkHttpServer 8080 区分）
HTTP_PORT="${HTTP_PORT:-8888}"
HTTP_BIND="${HTTP_BIND:-0.0.0.0}"

# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------
# 默认 debug：因 release 需 keystore.properties（仓库不入 .gitignore）
DEFAULT_BUILD_TYPE="${DEFAULT_BUILD_TYPE:-debug}"
KEYSTORE_PROPS="$REPO_ROOT/keystore.properties"

# 包名与主 Activity（用于设备冒烟测试）
APP_PKG="com.signalmontor.app"
APP_MAIN_ACTIVITY="$APP_PKG/.MainActivity"

# ---------------------------------------------------------------------------
# 通用工具函数
# ---------------------------------------------------------------------------
# 颜色（仅 tty 时启用）
if [[ -t 1 ]]; then
    C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
    C_BLUE=$'\033[34m'; C_CYAN=$'\033[36m'; C_BOLD=$'\033[1m'
    C_RESET=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_CYAN=""; C_BOLD=""; C_RESET=""
fi

log()   { printf "${C_GREEN}[harness]${C_RESET} %s\n" "$*"; }
warn()  { printf "${C_YELLOW}[warn]${C_RESET} %s\n" "$*" >&2; }
err()   { printf "${C_RED}[error]${C_RESET} %s\n" "$*" >&2; }
info()  { printf "${C_CYAN}[info]${C_RESET} %s\n" "$*"; }
step()  { printf "\n${C_BOLD}━━ %s ━━${C_RESET}\n" "$*"; }

die() { err "$*"; exit 1; }

# 幂等确保目录存在
ensure_dir() { mkdir -p "$@"; }

# 获取本机 IPv4（用于打印局域网下载地址）
get_lan_ip() {
    command -v ip >/dev/null 2>&1 && \
        ip -4 addr 2>/dev/null \
        | awk '/inet /{print $2}' | cut -d/ -f1 \
        | grep -v '^127\.' | head -1
    command -v hostname >/dev/null 2>&1 && hostname -I 2>/dev/null | awk '{print $1}'
}

# 读取当前版本号（来自 version.properties；注意 gradle 构建时会自增 minor）
read_version() {
    local vf="$REPO_ROOT/version.properties"
    [[ -f "$vf" ]] || { echo "0.0.0"; return; }
    local major minor patch
    major=$(awk -F= '/^major=/{print $2}' "$vf" | tr -d ' \r')
    minor=$(awk -F= '/^minor=/{print $2}' "$vf" | tr -d ' \r')
    patch=$(awk -F= '/^patch=/{print $2}' "$vf" | tr -d ' \r')
    echo "${major:-0}.${minor:-0}.${patch:-0}"
}

# 探测是否在 Termux/PRoot 内（判断 am/pm/termux-open 可用性）
in_termux() {
    [[ -x /data/data/com.termux/files/usr/bin/termux-open ]] || \
    [[ -x /data/data/com.termux/files/usr/bin/am ]] || \
    [[ -n "${TERMUX_VERSION:-}" ]]
}

# 探测包管理器
pkg_mgr() {
    if command -v apt-get >/dev/null 2>&1; then echo apt
    elif command -v dnf >/dev/null 2>&1; then echo dnf
    elif command -v pacman >/dev/null 2>&1; then echo pacman
    elif command -v apk >/dev/null 2>&1; then echo apk
    else echo none
    fi
}

ensure_dir "$DIST_DIR" "$LOG_DIR"
