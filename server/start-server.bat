@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist node_modules (
  echo 首次运行，正在安装依赖...
  call npm install --no-audit --no-fund
  if errorlevel 1 goto :failed
)
echo.
echo 弦谱曲库服务启动中，请保持此窗口开启。
echo 管理后台：http://localhost:8787
echo.
node server.js
goto :end

:failed
echo 依赖安装失败，请确认已安装 Node.js 18 或更高版本。
pause

:end
