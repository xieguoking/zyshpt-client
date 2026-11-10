#!/usr/bin/env bash
# 网关客户端 GUI 启动脚本（Linux / macOS，需 JDK 17+）
set -e
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAVA_BIN="java"
[ -n "$JAVA_HOME" ] && JAVA_BIN="$JAVA_HOME/bin/java"
exec "$JAVA_BIN" \
    --module-path "$APP_HOME/javafx" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "$APP_HOME/zyshpt-client.jar:$APP_HOME/libs/*" \
    com.shrs.zyshpt.GatewayFxApp "$@"
