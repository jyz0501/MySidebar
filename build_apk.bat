@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: 应用名（用于产出文件名）
set APPNAME=轻边栏

:: 读取 version.properties 的三段版本
set MAJOR=
set MINOR=
set PATCH=
for /f "usebackq tokens=1,* delims==" %%A in ("%CD%\version.properties") do (
    if "%%A"=="major" set "MAJOR=%%B"
    if "%%A"=="minor" set "MINOR=%%B"
    if "%%A"=="patch" set "PATCH=%%B"
)
if not defined MAJOR (
    echo 无法读取 version.properties 的版本号
    exit /b 1
)
set VER=%MAJOR%.%MINOR%.%PATCH%

:: 构建时间 yyyyMMddHHmm
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMddHHmm"') do set TS=%%i

:: 签名情况：存在 keystore.properties 视为正式签名(v2/v3)，否则回退 debug 签名(v2/v3)
if exist "%CD%\keystore.properties" (
    set SIGN=v2v3已签名
) else (
    set SIGN=v2v3调试签名
)

set OUT=%APPNAME%_v%VER%_%TS%_%SIGN%.apk

echo ==================================================
echo   轻边栏 构建  v%VER%  签名:%SIGN%  时间:%TS%
echo ==================================================

call gradlew.bat assembleRelease --no-daemon --console=plain
if errorlevel 1 (
    echo 构建失败
    exit /b 1
)

set SRC=%CD%\app\build\outputs\apk\release\app-release.apk
if not exist "%SRC%" (
    echo 未找到构建产物: %SRC%
    exit /b 1
)

copy /Y "%SRC%" "%CD%\%OUT%" >nul
echo.
echo 已产出: %OUT%
endlocal
