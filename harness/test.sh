#!/usr/bin/env bash
# test.sh — 自动化测试
# 三层：
#   1. 单元测试   ./gradlew test          （JVM，必过门槛）
#   2. 静态检查   ./gradlew lint          （必过门槛）
#   3. 设备冒烟   am start + 进程校验      （可选，需 --device）
# 测试不通过则非零退出，阻断 publish。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

step "自动化测试"

cd "$REPO_ROOT"

[[ -x ./gradlew ]] || die "缺少 gradlew，请先: harness/env.sh"

export JAVA_HOME
export ANDROID_HOME
export ANDROID_SDK_ROOT

RUN_DEVICE=0
[[ "${1:-}" == "--device" ]] && RUN_DEVICE=1

# ---------------------------------------------------------------------------
# 1. 单元测试
# ---------------------------------------------------------------------------
step "1/3  单元测试 (./gradlew test)"
UNIT_LOG="$LOG_DIR/test-unit-$(date +%Y%m%d-%H%M%S).log"
info "日志: $UNIT_LOG"
if ! ./gradlew test --no-daemon --console=plain 2>&1 | tee "$UNIT_LOG"; then
    err "单元测试失败"
    die "测试中止，未通过门槛"
fi
log "单元测试通过"

# ---------------------------------------------------------------------------
# 2. 静态检查
# ---------------------------------------------------------------------------
step "2/3  静态检查 (./gradlew lint)"
LINT_LOG="$LOG_DIR/lint-$(date +%Y%m%d-%H%M%S).log"
info "日志: $LINT_LOG"
if ! ./gradlew lint --no-daemon --console=plain 2>&1 | tee "$LINT_LOG"; then
    err "lint 检查失败"
    die "测试中止，未通过门槛"
fi
log "lint 通过"

# ---------------------------------------------------------------------------
# 3. 设备冒烟（可选）
# ---------------------------------------------------------------------------
if [[ "$RUN_DEVICE" == 1 ]]; then
    step "3/3  设备冒烟测试"
    if ! in_termux; then
        warn "非 Termux 环境，跳过设备冒烟"
    elif ! command -v pm >/dev/null 2>&1; then
        warn "无 pm 工具，跳过设备冒烟"
    elif ! pm list packages "$APP_PKG" 2>/dev/null | grep -q "$APP_PKG"; then
        warn "$APP_PKG 未安装，跳过设备冒烟（先运行 harness/install.sh）"
    else
        log "启动 $APP_MAIN_ACTIVITY"
        am start -n "$APP_MAIN_ACTIVITY" 2>&1 | sed 's/^/  /' || true
        sleep 3
        # 进程存活校验
        if pgrep -f "$APP_PKG" >/dev/null 2>&1 || \
           pidof "$(basename "$APP_PKG")" >/dev/null 2>&1; then
            log "进程存活，冒烟测试通过"
        else
            warn "未检测到进程，可能需手动确认权限弹窗"
        fi
    fi
else
    info "跳过设备冒烟（如需运行: harness/test.sh --device）"
fi

# ---------------------------------------------------------------------------
# 摘要
# ---------------------------------------------------------------------------
step "测试摘要"
{
    echo "单元测试 : PASS  ($UNIT_LOG)"
    echo "lint     : PASS  ($LINT_LOG)"
    if [[ "$RUN_DEVICE" == 1 ]]; then
        echo "设备冒烟 : attempted"
    else
        echo "设备冒烟 : skipped"
    fi
    echo "总测试   : 通过"
} | tee "$LOG_DIR/test-last.txt"

log "测试全部通过"
