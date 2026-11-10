@echo off
rem 网关客户端 GUI 启动脚本（Windows，需 JDK 17+）
setlocal
set "APP_HOME=%~dp0"
set "JAVA_BIN=java"
if defined JAVA_HOME set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
"%JAVA_BIN%" --module-path "%APP_HOME%javafx" --add-modules javafx.controls,javafx.fxml ^
    -cp "%APP_HOME%zyshpt-client.jar;%APP_HOME%libs\*" com.shrs.zyshpt.GatewayFxApp %*
