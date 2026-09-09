# ============================================================
# Fox-ai-agent 后端 · 微信云托管 / Docker 部署
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
# 微信云托管要求容器监听 80
EXPOSE 80
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai
# 密钥通过平台环境变量注入：FOX_DASHSCOPE_API_KEY（必填）、FOX_SEARCH_API_KEY（可选）
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=80"]
