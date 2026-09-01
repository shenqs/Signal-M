#!/bin/bash
# SignalMonitor APK Download Server (legacy entry, thin wrapper around harness/serve.sh)
# Port: 8888 (fixed, AGENTS.md convention)
# Directory: dist/ (built APKs collected by harness/build.sh)
#
# Recommended: ./harness/harness.sh serve [--stop]
# This script is kept for backward compatibility with AGENTS.md references.

PORT=8888
# Resolve repo root dynamically (this file lives at <repo-root>/start_server.sh)
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
DIR="$REPO_ROOT/dist"
LOG="$REPO_ROOT/harness/logs/http-server.log"

mkdir -p "$DIR" "$(dirname "$LOG")"

# Fallback: if no built APK, serve repo root so old URLs still work
[[ -f "$DIR/app-latest.apk" ]] || DIR="$REPO_ROOT"

# Kill existing server if running
pkill -f "http.server $PORT" 2>/dev/null

# Start server in background
nohup python3 -m http.server $PORT --bind 0.0.0.0 --directory "$DIR" > "$LOG" 2>&1 &

sleep 1

# Verify server started
if pgrep -f "http.server $PORT" > /dev/null; then
    echo "Server started successfully on port $PORT"
    echo "Serving: $DIR"
    echo "APK download URL: http://<server-ip>:$PORT/app-latest.apk"
    echo "Log file: $LOG"
else
    echo "Failed to start server"
    exit 1
fi