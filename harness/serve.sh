#!/usr/bin/env bash
# serve.sh — HTTP 分发服务
# 在 8888 端口（AGENTS.md 约定）启动静态服务器，向宿主 Android 提供 APK 下载。
# 与 app 内置 ApkHttpServer.kt（8080，应用运行时分发）职责区分。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

# --stop 子命令优先处理
if [[ "${1:-}" == "--stop" ]]; then
    step "停止 HTTP 服务"
    if [[ -f "$LOG_DIR/http-server.pid" ]]; then
        PID=$(cat "$LOG_DIR/http-server.pid")
        kill "$PID" 2>/dev/null && log "已停止 PID $PID" || warn "PID $PID 不存在"
        rm -f "$LOG_DIR/http-server.pid"
    fi
    pkill -f "http.server $HTTP_PORT" 2>/dev/null && log "已清理残留进程" || true
    exit 0
fi

step "启动 HTTP 分发服务 (端口 $HTTP_PORT)"

# 1. 产物存在性
if ! ls "$DIST_DIR"/*.apk >/dev/null 2>&1; then
    die "dist/ 下无 APK，请先运行: harness/build.sh"
fi
LATEST="$DIST_DIR/app-latest.apk"
[[ -e "$LATEST" ]] || LATEST="$(ls -t "$DIST_DIR"/*.apk | head -1)"

# 2. 杀掉旧实例
pkill -f "http.server $HTTP_PORT" 2>/dev/null || true
sleep 0.5

# 3. 启动
SERVE_LOG="$LOG_DIR/http-server.log"
info "服务目录: $DIST_DIR"
info "日志: $SERVE_LOG"

cd "$DIST_DIR"
nohup python3 -m http.server "$HTTP_PORT" --bind "$HTTP_BIND" \
    > "$SERVE_LOG" 2>&1 &
SERVE_PID=$!
echo "$SERVE_PID" > "$LOG_DIR/http-server.pid"
sleep 1

# 4. 健康检查
if ! kill -0 "$SERVE_PID" 2>/dev/null; then
    err "服务器未启动，日志: $SERVE_LOG"
    cat "$SERVE_LOG" >&2 || true
    die "服务器启动失败"
fi

LAN_IP="$(get_lan_ip | head -1)"

step "下载地址"
{
    echo "本机  : http://127.0.0.1:$HTTP_PORT/"
    [[ -n "$LAN_IP" ]] && echo "局域网: http://$LAN_IP:$HTTP_PORT/"
    echo
    echo "首页      : /"
    echo "最新 APK  : /app-latest.apk"
    echo "全部文件  : /  (目录列表)"
    echo
    echo "PID       : $SERVE_PID"
    echo "停止      : kill $SERVE_PID 或 harness/serve.sh --stop"
} | tee "$LOG_DIR/serve-endpoints.txt"

log "HTTP 分发服务运行中"
