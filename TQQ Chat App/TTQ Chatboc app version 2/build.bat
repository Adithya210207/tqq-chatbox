@echo off
rem Compiles the app into the "out" folder.
cd /d "%~dp0"
set FX=lib\javafx-sdk-26.0.1\lib
if exist out rmdir /s /q out
javac --module-path "%FX%" --add-modules javafx.controls -encoding UTF-8 -d out src\tqq\*.java
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    pause
    exit /b 1
)
echo Build OK
