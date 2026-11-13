@echo off
rem Makes a real Windows app: "TQQ Chatbox v1\TQQ Chatbox v1.exe" (with its own Java runtime)
rem plus a shortcut next to this file. Run it again after changing the code.
rem The exe uses THIS folder's assets\ (pictures, style.css) and history\ (chats).
setlocal
cd /d "%~dp0"
set NAME=TQQ Chatbox v1
set FXSDK=lib\javafx-sdk-26.0.1

call "%~dp0build.bat"
if not exist out\tqq\Main.class exit /b 1

echo Preparing files...
if exist pkg rmdir /s /q pkg
mkdir pkg\input\fx
jar --create --file pkg\input\tqq.jar -C out .
rem only the JavaFX parts this app uses (no web browser engine / media = ~95 MB smaller)
mkdir pkg\input\fx\lib pkg\input\fx\bin
for %%J in (javafx.base javafx.controls javafx.graphics) do copy /y "%FXSDK%\lib\%%J.jar" pkg\input\fx\lib >nul
xcopy /q /y "%FXSDK%\bin\*.dll" pkg\input\fx\bin >nul
for %%D in (jfxwebkit gstreamer-lite glib-lite jfxmedia fxplugins) do del /q "pkg\input\fx\bin\%%D.dll"

if exist "%NAME%" rmdir /s /q "%NAME%"
echo Packaging (takes about a minute)...
jpackage --type app-image --name "%NAME%" --dest . ^
  --input pkg\input --main-jar tqq.jar --main-class tqq.Main ^
  --icon assets\app.ico ^
  --add-modules java.se,jdk.unsupported,jdk.charsets ^
  --java-options "--module-path=$APPDIR\fx\lib" ^
  --java-options "--add-modules=javafx.controls" ^
  --java-options "--enable-native-access=javafx.graphics" ^
  --java-options "-Dtqq.home=$APPDIR\..\.."
set ERR=%errorlevel%
rmdir /s /q pkg
if not "%ERR%"=="0" (
    echo PACKAGING FAILED
    pause
    exit /b 1
)

rem JavaFX runs from the module path; take its jars off the classpath jpackage added them to
powershell -NoProfile -Command "$c='%CD%\%NAME%\app\%NAME%.cfg'; (Get-Content $c) | Where-Object { $_ -notlike 'app.classpath=$APPDIR\fx\*' } | Set-Content $c"

rem shortcut next to this file
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%CD%\%NAME%.lnk'); $s.TargetPath='%CD%\%NAME%\%NAME%.exe'; $s.WorkingDirectory='%CD%'; $s.IconLocation='%CD%\%NAME%\%NAME%.exe,0'; $s.Save()"
echo.
echo Done: "%CD%\%NAME%\%NAME%.exe"
