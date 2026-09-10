# ============================================================
# 前端镜像：Node 构建 → Nginx 托管（含 /api 反向代理）
# 云托管部署：上传 frontend/ 目录代码包，平台按本 Dockerfile 构建，端口 80
# ============================================================

FROM node:20-alpine AS build
WORKDIR /app
# 先拷依赖清单，利用层缓存加速
COPY package.json package-lock.json ./
RUN npm install --no-audit --no-fund
# 再拷源码并构建
COPY . .
RUN npm run build

# ---- 运行阶段：Nginx ----
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
