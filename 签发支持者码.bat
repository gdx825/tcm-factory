@echo off
chcp 65001 >nul
title 中医工厂 - 支持者码签发
cd /d "%~dp0"

echo.
echo   ============================================================
echo     中医工厂 · 支持者码签发
echo   ============================================================
echo.

where python >nul 2>nul
if errorlevel 1 goto nopython

python "tools\issue_supporter_code.py"
if errorlevel 1 goto failed

echo.
pause
exit /b 0

:nopython
echo   [x] 没有检测到 Python。
echo.
echo   请先到 python.org 下载安装 Python（安装时务必勾选
echo   "Add Python to PATH"），装完重新双击本文件。
echo.
echo   如果提示的是 "Microsoft Store" 版本的 python，请到
echo   设置 - 应用 - 高级应用设置 - 应用执行别名 里把
echo   python.exe / python3.exe 两个开关关掉。
echo.
pause
exit /b 1

:failed
echo.
echo   签发过程中出错了，上面有具体原因。
echo.
pause
exit /b 1
