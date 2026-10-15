@echo off
chcp 65001 >nul
title 中医工厂 - 发布知识库更新
cd /d "%~dp0"

echo.
echo   ============================================================
echo     中医工厂 · 重新生成知识库发布目录
echo   ============================================================
echo.
echo   用途：改过 app\src\main\assets\knowledge\ 里的内容后，
echo         把新内容同步到 kb\ 目录（数据源直接读它）。
echo.
echo   只改支持者名单的话不用跑这个 —— 签发支持者码时会自动做。
echo.

where python >nul 2>nul
if errorlevel 1 goto nopython

python "tools\sync_kb.py"
if errorlevel 1 goto failed

echo.
echo   推送到 GitHub 后，用户打开 App 就能在「知识库」里看到更新。
echo.
pause
exit /b 0

:nopython
echo   [x] 没有检测到 Python。
echo.
echo   请先到 python.org 下载安装 Python（安装时务必勾选
echo   "Add Python to PATH"），装完重新双击本文件。
echo.
pause
exit /b 1

:failed
echo.
echo   生成失败，上面有具体原因。
echo.
pause
exit /b 1
