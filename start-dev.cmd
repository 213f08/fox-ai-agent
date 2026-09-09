@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  Fox AI Agent 一键启动（Windows）
rem  启动顺序有依赖：MCP 图片搜索服务(8127) -> 主应用(8123) -> 前端(5173)
rem ============================================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MVN=C:\Program Files (x86)\apache-maven-3.9.11\bin\mvn.cmd"
set "ROOT=%~dp0"

rem 坑：本机存在环境变量 SERVER__PORT=51308，Spring Boot 的 relaxed binding 会把它
rem 当成 server.port，覆盖 yml 里的配置。必须显式用命令行参数覆盖，优先级最高。
echo [1/3] 启动 MCP 图片搜索服务 :8127 ...
start "fox-mcp-server" "%MVN%" -f "%ROOT%fox-image-search-mcp-server\pom.xml" -B -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--server.port=8127"

rem 等 MCP 起来再启动主应用，否则主应用 MCP 客户端握手超时(20s)会直接拖崩启动
timeout /t 25 /nobreak >nul

echo [2/3] 启动主应用 :8123 ...
start "fox-ai-agent" "%MVN%" -f "%ROOT%pom.xml" -B -DskipTests spring-boot:run "-Dspring-boot.run.arguments=--server.port=8123"

echo [3/3] 启动前端 :5173 ...
start "fox-frontend" cmd /c "cd /d "%ROOT%frontend" && npm run dev"

echo.
echo 启动完成，访问地址：
echo   后端接口根路径 : http://localhost:8123/api
echo   Knife4j 文档   : http://localhost:8123/api/doc.html
echo   前端页面       : http://localhost:5173
echo.
pause
