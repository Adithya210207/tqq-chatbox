@echo off
rem Starts the TQQ Chatbox (builds it first if needed).
cd /d "%~dp0"
if not exist out\tqq\Main.class call build.bat
if not exist out\tqq\Main.class exit /b 1
start "" javaw --module-path "lib\javafx-sdk-26.0.1\lib" --add-modules javafx.controls --enable-native-access=javafx.graphics -cp out tqq.Main
