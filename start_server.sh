#!/bin/bash
# SignalMonitor APK Download Server
# Port: 8888 (fixed)
# Directory: /root/SignalMonitor

PORT=8888
DIR=/root/SignalMonitor
LOG=/tmp/signalmonitor_http.log

# Kill existing server if running
pkill -f "http.server $PORT" 2>/dev/null

# Start server in background
nohup python3 -m http.server $PORT --bind 0.0.0.0 --directory $DIR > $LOG 2>&1 &

sleep 1

# Verify server started
if pgrep -f "http.server $PORT" > /dev/null; then
    echo "Server started successfully on port $PORT"
    echo "APK download URL: http://<server-ip>:$PORT/"
    echo "Log file: $LOG"
else
    echo "Failed to start server"
    exit 1
fi