#!/bin/bash

APP_NAME="fund-agent-mcp-server"
JAR_NAME="fund-agent-mcp-server-1.0.0-SNAPSHOT.jar"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$APP_DIR/logs"
PID_FILE="$APP_DIR/${APP_NAME}.pid"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE")"
  if ps -p "$OLD_PID" >/dev/null 2>&1; then
    echo "$APP_NAME is already running, pid=$OLD_PID"
    exit 1
  fi
fi

nohup java \
  -server \
  -Xms512m \
  -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath="$LOG_DIR" \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar "$APP_DIR/$JAR_NAME" \
  --server.port=8091 \
  > "$LOG_DIR/${APP_NAME}.log" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
echo "$APP_NAME started, pid=$PID"
echo "log: $LOG_DIR/${APP_NAME}.log"