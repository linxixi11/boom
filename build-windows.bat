@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================================
echo     BOM 管理系统 - Windows 安装包构建脚本
echo ============================================================
echo.

:: ---- 检查 JDK 版本 ----
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请先安装 JDK 17 或更高版本
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

for /f "tokens=3" %%a in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%a
)
set JAVA_VER=%JAVA_VER:"=%
for /f "delims=." %%a in ("%JAVA_VER%") do set JAVA_MAJOR=%%a

if %JAVA_MAJOR% LSS 17 (
    echo [错误] 需要 JDK 17 或更高版本，当前版本: %JAVA_VER%
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)
echo [OK] Java 版本: %JAVA_VER%

:: ---- 检查 Maven ----
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven，请先安装 Maven
    echo 下载地址: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
echo [OK] Maven 已就绪

:: ---- 构建 Fat JAR ----
echo.
echo [1/2] 构建 Fat JAR...
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [错误] Maven 构建失败
    pause
    exit /b 1
)
echo [OK] Fat JAR 构建成功

:: ---- 生成安装包 ----
echo.
echo [2/2] 生成 Windows 安装包...
if not exist dist mkdir dist

set APP_NAME=BOM管理系统
set APP_VERSION=1.0.0
set MAIN_JAR=bom-manager-%APP_VERSION%.jar
set ICON=app.ico

set JPKG_OPTS=--input target/ ^
    --main-jar %MAIN_JAR% ^
    --main-class com.bom.Main ^
    --name "%APP_NAME%" ^
    --app-version %APP_VERSION% ^
    --type msi ^
    --win-dir-chooser ^
    --win-shortcut ^
    --win-menu ^
    --win-menu-group "BOM管理系统" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Duser.language=zh" ^
    --java-options "-Duser.country=CN" ^
    --dest dist

:: 如果有图标文件则使用
if exist %ICON% (
    set JPKG_OPTS=%JPKG_OPTS% --icon %ICON%
)

jpackage %JPKG_OPTS%
if %errorlevel% neq 0 (
    echo [错误] jpackage 打包失败
    pause
    exit /b 1
)

echo.
echo ============================================================
echo     构建完成！
echo     安装包位置: dist\%APP_NAME%-%APP_VERSION%.msi
echo ============================================================
echo.
echo 将 .msi 文件分发给用户，双击安装即可使用（无需安装 Java）。
echo.
pause
