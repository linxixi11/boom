@echo off
chcp 65001 >nul 2>&1
setlocal

echo ============================================================
echo     BOM 管理系统 - Windows 安装包构建脚本
echo ============================================================
echo.

set SCRIPT=%~dp0scripts\windows\build-installer.ps1

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" %*
if %errorlevel% neq 0 (
    echo.
    echo [错误] Windows 安装包构建失败
    pause
    exit /b %errorlevel%
)

echo.
echo ============================================================
echo     构建完成！
echo     安装包位置: dist\windows
echo ============================================================
echo.
echo 将 .exe 或 .msi 文件分发给用户，双击安装即可使用（无需安装 Java）。
echo.
pause
