#!/usr/bin/env bash
# install.sh — 把 APK 安装到宿主 Android
# 流程：从 8888 端口下载 APK 到宿主共享存储 → 调用系统安装器 → 校验安装成功。
# 仅 Termux/PRoot 环境可用（依赖 am/pm/termux-open）。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

step "安装 APK 到宿主 Android"

# 1. 环境探测
if ! in_termux; then
    err "未检测到 Termux 工具链 (am/pm/termux-open)"
    die "本脚本仅在 Termux 或 PRoot-on-Termux 内可用"
fi
log "Termux 环境已确认"

# 2. HTTP 服务在线检查
if ! pgrep -f "http.server $HTTP_PORT" >/dev/null 2>&1; then
    die "HTTP 服务未运行，请先: harness/serve.sh"
fi
info "HTTP 服务在线 (端口 $HTTP_PORT)"

# 3. 选择源：优先命令行参数，否则用 latest
APK_URL="${1:-http://127.0.0.1:$HTTP_PORT/app-latest.apk}"
APK_NAME="$(basename "$APK_URL")"

info "源: $APK_URL"

# 4. 确定宿主可访问的下载目录（PRoot 视角与宿主视角需重叠）
#    候选 1：Termux 共享存储（termux-setup-storage 后挂载）
#    候选 2：Termux home 私有目录（termux-open 的 FileProvider 可转发给安装器）
resolve_download_dir() {
    if [[ -d /data/data/com.termux/files/home/storage/downloads ]]; then
        echo "/data/data/com.termux/files/home/storage/downloads"
        return
    fi
    # 尝试唤醒存储授权
    if command -v termux-setup-storage >/dev/null 2>&1; then
        warn "共享存储未挂载，尝试 termux-setup-storage（请在手机上确认授权）"
        termux-setup-storage 2>/dev/null || true
        sleep 2
        if [[ -d /data/data/com.termux/files/home/storage/downloads ]]; then
            echo "/data/data/com.termux/files/home/storage/downloads"
            return
        fi
    fi
    mkdir -p /data/data/com.termux/files/home/downloads
    echo "/data/data/com.termux/files/home/downloads"
}

DL_DIR="$(resolve_download_dir)"
DST="$DL_DIR/$APK_NAME"
info "下载目录: $DL_DIR"
info "目标    : $DST"

# 5. 下载
step "下载 APK"
curl -fL --progress-bar -o "$DST" "$APK_URL" || \
    wget -q -O "$DST" "$APK_URL" || die "下载失败"
log "已下载: $DST ($(du -h "$DST" | awk '{print $1}'))"

# 6. 卸载旧版本（避免签名不一致导致安装失败）
step "清理旧版本"
if command -v pm >/dev/null 2>&1; then
    if pm list packages "$APP_PKG" 2>/dev/null | grep -q "$APP_PKG"; then
        log "卸载旧 $APP_PKG"
        pm uninstall "$APP_PKG" 2>/dev/null || warn "pm uninstall 失败（可能无 root）"
    else
        log "无旧版本"
    fi
fi

# 7. 触发系统安装器
step "触发系统安装器"
if command -v termux-open >/dev/null 2>&1; then
    log "使用 termux-open 唤起安装器"
    termux-open "$DST" 2>/dev/null || true
elif command -v am >/dev/null 2>&1; then
    log "使用 am start 唤起安装器"
    am start -a android.intent.action.VIEW \
        -d "file://$DST" \
        -t "application/vnd.android.package-archive" 2>/dev/null || true
else
    warn "无可用启动器，请手动在文件管理器中点击: $DST"
fi

# 8. 等待用户确认安装（最多 120 秒）
step "等待安装完成（请在手机上确认）"
INSTALLED=0
for i in $(seq 1 60); do
    if command -v pm >/dev/null 2>&1 && pm list packages "$APP_PKG" 2>/dev/null | grep -q "$APP_PKG"; then
        INSTALLED=1
        break
    fi
    printf "\r  等待中 (%02d/60)..." "$i"
    sleep 2
done
echo

# 9. 终态校验
if [[ "$INSTALLED" == 1 ]]; then
    log "安装成功: $APP_PKG"
    pm list packages "$APP_PKG" 2>/dev/null | sed 's/^/  /'
else
    warn "120 秒内未检测到安装"
    warn "若仍需安装，请在手机文件管理器中手动点击: $DST"
    warn "确认后可继续: harness/test.sh --device"
fi

log "安装流程结束"
