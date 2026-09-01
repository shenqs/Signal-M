#!/usr/bin/env bash
# build.sh — 构建 APK
# 默认 debug；若存在 keystore.properties 则可 release。
# 构建前 version.properties 由 gradle 自增 minor（见 app/build.gradle.kts）。
# 产物归集到 dist/ 并按版本号命名。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mirror.sh"

BUILD_TYPE="${1:-$DEFAULT_BUILD_TYPE}"
[[ "$BUILD_TYPE" =~ ^(debug|release)$ ]] || die "构建类型必须为 debug 或 release，收到: $BUILD_TYPE"

step "构建 APK ($BUILD_TYPE)"

cd "$REPO_ROOT"

# 确保 Maven 镜像 init 就位（env.sh 未跑完整时兜底）
[[ -f "$GRADLE_INIT_FILE" ]] || write_gradle_init
# 确保 wrapper distributionUrl 指向镜像
[[ -f "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" ]] && patch_gradle_wrapper_mirror || true

# 1. 前置依赖：wrapper + local.properties
[[ -x ./gradlew ]] || die "缺少 gradlew，请先运行: harness/env.sh"
[[ -f "$LOCAL_PROPS" ]] || die "缺少 local.properties，请先运行: harness/env.sh"

# 2. release 模式必须有 keystore
if [[ "$BUILD_TYPE" == release ]]; then
    if [[ ! -f "$KEYSTORE_PROPS" ]]; then
        err "release 构建需要 $KEYSTORE_PROPS（仓库不入 git）"
        die "请放置签名配置或改用: harness/build.sh debug"
    fi
    log "已检测到 keystore.properties"
else
    log "debug 构建，无需签名配置"
fi

# 3. 记录构建前版本（gradle 会自增 minor）
V_BEFORE=$(read_version)
log "构建前版本: $V_BEFORE"

# 4. 清理 + 构建
step "执行 ./gradlew clean assemble${BUILD_TYPE^}"
export JAVA_HOME
export ANDROID_HOME
export ANDROID_SDK_ROOT

BUILD_LOG="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S).log"
info "构建日志: $BUILD_LOG"

if ! ./gradlew clean "assemble${BUILD_TYPE^}" --no-daemon --console=plain 2>&1 | tee "$BUILD_LOG"; then
    err "构建失败，详见 $BUILD_LOG"
    die "构建中止"
fi

# 5. 定位产物
V_AFTER=$(read_version)
log "构建后版本: $V_AFTER"

APK_SRC="$APP_DIR/build/outputs/apk/$BUILD_TYPE"
APK_FILE="$APK_SRC/app-$BUILD_TYPE.apk"
[[ -f "$APK_FILE" ]] || die "未找到产物: $APK_FILE"

# 6. 归集到 dist/（版本号命名）
ensure_dir "$DIST_DIR"
APK_NAME="app-$BUILD_TYPE-v$V_AFTER.apk"
APK_DST="$DIST_DIR/$APK_NAME"
cp -f "$APK_FILE" "$APK_DST"
log "产物: $APK_DST"

# 同时保留 latest 软链，便于 serve/install 直接引用
ln -sf "$APK_NAME" "$DIST_DIR/app-$BUILD_TYPE-latest.apk"
ln -sf "$APK_NAME" "$DIST_DIR/app-latest.apk"

# 7. 摘要
step "构建摘要"
{
    echo "构建类型  : $BUILD_TYPE"
    echo "版本      : $V_BEFORE -> $V_AFTER"
    echo "产物      : $APK_DST"
    echo "大小      : $(du -h "$APK_DST" | awk '{print $1}')"
    echo "SHA-256   : $(sha256sum "$APK_DST" | awk '{print $1}')"
    echo "构建日志  : $BUILD_LOG"
} | tee "$LOG_DIR/build-last.txt"

log "构建完成"
echo "$APK_DST" > "$LOG_DIR/last-apk-path.txt"
