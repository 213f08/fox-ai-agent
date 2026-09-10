# ============================================================
# Fox-ai-agent 后端 · Docker 部署（微信云托管 / Render 通用）
# ------------------------------------------------------------
# 多阶段构建：无需本机安装 Maven / JDK，平台拉代码后按本文件构建。
#
# 使用方式（任选）：
#   1. 本机构建并推送：docker build -t fox-ai-agent . && docker push ...
#   2. 微信云托管：把代码打包上传（或关联 git 仓库），平台自动用本 Dockerfile 构建；
#      然后在"服务配置"里填：
#        - 端口：80
#        - 健康检查路径：/api/doc.html
#        - 环境变量：SPRING_PROFILES_ACTIVE=prod、FOX_DASHSCOPE_API_KEY=xxx、
#          FOX_SEARCH_API_KEY=xxx（生产 profile 默认不连 MCP / 不连数据库，单容器即可启动）
#   3. Render：New Web Service → 选本仓库 → Runtime 选 Docker → 平台自动读本文件；
#      端口由平台注入的 PORT 环境变量决定，无需手工指定。
#      环境变量同上（SPRING_PROFILES_ACTIVE / FOX_DASHSCOPE_API_KEY / FOX_SEARCH_API_KEY）。
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先拷贝 pom.xml，单独拉依赖，利用 Docker 层缓存加速后续构建
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true
# 拷贝源码并打包（测试会真调大模型烧额度，一律跳过）
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/Fox-ai-agent-0.0.1-SNAPSHOT.jar app.jar
# 端口按平台自适应：
#   - 微信云托管：不注入 PORT，回退到 80（云托管要求容器监听 80）
#   - Render / Railway / Fly 等：平台注入 PORT（Render 默认 10000），自动跟随
# 用 shell 形式启动才能展开 ${PORT}，exec 保证 SIGTERM 能透传给 JVM（优雅停机）
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
# 密钥通过平台环境变量注入：FOX_DASHSCOPE_API_KEY（必填）、FOX_SEARCH_API_KEY（可选）
ENTRYPOINT ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-80}"]
