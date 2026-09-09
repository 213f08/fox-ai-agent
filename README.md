# Fox AI Agent · 智能体百宝箱

<div align="center">

**一个 Spring AI 学习驱动的"智能体能力百宝箱"项目**

每个 `App` 是一个独立能力槽位：饮食健康对话 · RAG 检索 · 工具调用 · 服务报告 ……  
跟着鱼皮《AI 超级智能体实战》打底，在每个槽位里塞进真实业务场景练习。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2-blue)](https://github.com/alibaba/spring-ai-alibaba)
[![DashScope](https://img.shields.io/badge/ChatModel-kimi--k2.7--code-ff6a00)](https://help.aliyun.com/zh/model-studio)
[![License](https://img.shields.io/badge/license-MIT-green)](#license)

</div>

---

## 📖 项目定位

> **不是**线上可用的医疗咨询系统，**是**用 Spring AI 把 AI 应用核心能力做一遍的学习型"百宝箱"。
> 当前主线应用是一个**饮食健康助手「小养」**（原「不烦」智能客服改造）：减脂 / 增肌 / 控糖 / 慢病膳食 / 营养科普，明确不诊断、不替代医嘱。

- **每个 App 一个能力**：可独立 demo / 测试 / 拼装组合
- **每个能力对应面试一个考点**：RAG / 工具调用 / 持久化 / 结构化输出 / 多 Agent / 流式输出
- **可部署到云**：微信云托管 / 阿里云函数计算（FC）/ 任意 Docker 环境（fat jar + Dockerfile）

---

## 🧰 百宝箱能力清单

| App / 模块 | 能力 | 状态 | 入口 |
|---|---|---|---|
| `CustomerApp「小养」` | 饮食健康对话（多轮 + Kryo 文件记忆 + token 级 SSE 流式） | ✅ 已完成 | `com.example.foxaiagent.app.CustomerApp` |
| `CustomerApp` | 饮食健康 RAG 知识库问答（本地 document/*.md 或百炼云知识库） | ✅ 已完成 | `doChatWithRag` 方法 |
| `CustomerApp` | 结构化输出（服务报告 JSON） | ✅ 已完成 | `doChatWithReport` 方法 |
| `CustomerApp` | 自定义 Advisor（日志 / ReReading） | ✅ 已完成 | `com.example.foxaiagent.advisor.*` |
| `FoxManus` | 全能智能体：ReAct 循环 + 工具调用 + MCP 图片搜索 + 流式输出 | ✅ 已完成 | `com.example.foxaiagent.agent.model.FoxManus` |
| `StreamingChatApp` | 流式输出（SSE，token 级） | ✅ 已完成 | `/api/ai/customer_app/chat/sse` |
| `MultimodalApp` | 多模态（图片理解） | 📋 计划中 | - |
| `McpApp` | MCP 协议对接（已落地图片搜索 MCP） | 🔶 部分完成 | `fox-image-search-mcp-server` |
| `MultiAgentWorkflow` | 多 Agent 协作 | 📋 计划中 | - |

---

## 🚀 快速开始

### 前置条件

- **JDK 21+**（推荐 Amazon Corretto 21）
- **Maven 3.8+**（或使用 IDEA 自带 Maven）
- **DashScope API Key**：在 [阿里云百炼控制台](https://dashscope.aliyun.com/apiKey) 申请

### 本地启动

```bash
# 1. 克隆代码
git clone https://gitee.com/maguawing/fox-ai-agent.git
cd fox-ai-agent

# 2. 配置 API Key
#    创建 src/main/resources/application-local.yml，写入：
#    spring:
#      ai:
#        dashscope:
#          api-key: sk-你的真实key
#    更多可选项（模型、搜索 key、MCP 地址）见 .env.example 与 application-prod.yml 顶部注释

# 3. 启动主应用（IDEA 直接跑 FoxAiAgentApplication 也行）
#    Windows 用户：mvnw 的 wrapper jar 未入库，直接双击 start-dev.cmd 最省事
./mvnw spring-boot:run

# 4. 访问
#    接口：http://localhost:8123/api
#    文档：http://localhost:8123/api/doc.html（Knife4j）
#    前端：http://localhost:5173（npm run dev，见 frontend/）
```

### 第一次调用

```bash
# 饮食健康助手多轮对话（同步，带记忆：同一 chatId 共享上下文）
curl "http://localhost:8123/api/ai/customer_app/chat/sync?message=减脂期晚上能吃主食吗&chatId=test1"

# 同上，SSE 流式（token 级打字机效果）
curl -N "http://localhost:8123/api/ai/customer_app/chat/sse?message=帮我配一份减脂晚餐&chatId=test2"

# 全能智能体 FoxManus（ReAct + 工具调用 + 联网搜索）
curl -N "http://localhost:8123/api/ai/manus/chat?message=搜一下低GI水果有哪些&chatId=test3"
```

---

## 📚 知识库（饮食健康 RAG Demo）

面向饮食健康助手「小养」的知识文档，作为 RAG 能力的检索源：

```
src/main/resources/document/
├── 01-减脂饮食指南.md        # 热量缺口 / 三大营养素分配
├── 02-增肌饮食指南.md        # 热量盈余 / 蛋白质 1.6~2.2g/kg
├── 03-控糖饮食指南.md        # GI/GL / 主食置换 / 隐形糖
├── 04-高血压DASH饮食指南.md   # 钠<2000mg / 高钾镁钙
├── 05-痛风高尿酸饮食指南.md   # 急性/缓解期分级 / 果糖陷阱
├── 06-维生素矿物质指南.md     # VD/B12/铁/钙/锌来源与误区
└── 07-饮食误区FAQ.md          # 断碳 / 骨头汤补钙 / 喝粥养胃…
```

**写作要点**：标题清晰、条目自包含（切分友好），刻意埋了检索陷阱（如"控糖≠无糖"、"骨头汤钙仅约 10mg/100ml"、"果汁糖≈2~3 个橙子"、"急性期可吃豆制品"），可作为 RAG 精度测试用例。

**云端知识库**：若走百炼云 RAG（`CustomerApp.doChatWithRag` 里的云 Advisor 分支），需在[百炼控制台](https://bailian.console.aliyun.com)创建同名知识库「饮食健康知识库」并上传 `document/*.md`。本地开发默认走本地 SimpleVectorStore（无云库也能演示）。

---

## 🛠️ 技术栈

| 类别 | 技术 | 版本 | 作用 |
|---|---|---|---|
| **核心框架** | Spring Boot | 3.5.10 | Web 容器 / Bean 管理 |
| **JDK** | Amazon Corretto / Microsoft OpenJDK | 21 | 虚拟线程 / Record Pattern |
| **AI 框架** | Spring AI Alibaba | 1.1.2.0 | Agent 编排 / ChatClient / Advisor |
| **AI 模型** | DashScope 百炼 | kimi-k2.7-code（默认） | 阿里云百炼大模型（多模态接口） |
| **Embedding** | DashScope | text-embedding-v3 | 向量化 |
| **向量库** | SimpleVectorStore | 内存版 | P1 演示，重启丢 |
| **持久化** | Kryo | 5.6.2 | 文件版对话记忆序列化 |
| **文档加载** | spring-ai-markdown-document-reader | 1.0.0-M6 | Markdown → Document |
| **接口文档** | Knife4j | 4.4.0 | Swagger UI 增强 |
| **工具集** | Hutool / Lombok | 5.8.46 / 1.18.36 | 工具类 / 注解 |
| **备用方案** | LangChain4j / 原生 SDK | - | 对照学习，pom 中保留 |

> 📌 同时引入 DashScope 原生 SDK、Spring AI Alibaba、LangChain4j 三种接入方式，**只用了 Spring AI Alibaba** 作为主力，其他留作学习对照。

---

## 📐 架构与代码结构

```
src/main/java/com/example/foxaiagent/
├── FoxAiAgentApplication.java     # Spring Boot 启动类
├── advisor/                       # 自定义 Advisor
│   ├── MyLoggerAdvisor.java       # 请求/响应日志埋点
│   └── ReReadingAdvisor.java      # Re2 提升推理质量
├── app/
│   └── CustomerApp.java           # 主应用：「小养」饮食健康助手
├── agent/model/                   # 自研 ReAct Agent 框架
│   ├── BaseAgent.java             # 生命周期 / run / token 级流式 SSE
│   ├── ReActAgent.java            # think → act 模板方法
│   ├── ToolCallAgent.java         # 工具调用（.stream() 边推边聚合 tool call）
│   └── FoxManus.java              # 全能智能体：搜索/抓取/生图/PDF
├── chatmemory/
│   ├── FileBaseChatMemory.java    # 自定义 Kryo 文件版 ChatMemory
│   └── KryoMemoryViewer.java      # 调试工具：查看 .kryo 文件内容
├── rag/                           # RAG 模块（百宝箱能力）
│   ├── CustomerAppDocumentLoader.java  # Markdown 文档加载
│   ├── CustomerAppVectorStoreConfig.java  # 本地 SimpleVectorStore（@Profile("!prod")）
│   └── CustomerAppCloudAdvisorConfig.java # 百炼云知识库检索增强
└── demo/invoke/                   # 四种调用方式 Demo
    ├── SdkAiInvoke.java           # DashScope SDK
    ├── HttpAiInvoke.java          # 原生 HTTP
    ├── SpringAiAiInvoke.java      # Spring AI（主力）
    └── LangChainAiInvoke.java     # LangChain4j

src/main/resources/
├── application.yml                # 公共配置（profile 占位、MCP 说明、端口 8123）
├── application-local.yml          # 本地密钥（gitignored，仅开发）
├── application-prod.yml           # 生产配置（全环境变量占位，无密钥）
└── document/                      # 饮食健康知识库文档
```

> 图片搜索 MCP 子项目见 `fox-image-search-mcp-server/`（SSE 端口 8127），仅本地开发需要；
> 生产单容器部署默认不启用（见 `application-prod.yml` 顶部注释）。

---

## 🎓 学习路线

按这个顺序看代码，1 周就能掌握 Spring AI 核心：

| 阶段 | 章节 | 关键概念 | 对应代码 |
|---|---|---|---|
| ① | LLM 接入 | ChatModel / ChatClient | `SpringAiAiInvoke` |
| ② | 多轮对话 | ChatMemory + Advisor | `CustomerApp`（构造） |
| ③ | 自定义记忆 | 实现 `ChatMemory` 接口 | `FileBaseChatMemory` |
| ④ | RAG 基础 | Document / Embedding / VectorStore | `CustomerApp.doChatWithRag` |
| ⑤ | 工具调用 | `@Tool` / ToolCallingManager | `ToolCallAgent` / `FoxManus` |
| ⑥ | 流式输出 | SSE / Flux / token 级推送 | `BaseAgent.runStream` / `thinkStream` |
| ⑦ | 结构化输出 | `.entity()` Bean → JSON Schema | `CustomerApp.doChatWithReport` |
| ⑧ | MCP | Model Context Protocol | `fox-image-search-mcp-server` + `doChatWithMCP` |

---

## ⚠️ 踩坑清单

部署前必看的 4 个坑（每条都有详细解释）：

### 1. API Key 不能进仓库
```
❌ 错：application.yml 直接写 sk-xxx，push 到 gitee 后密钥泄漏
✅ 对：本地 key 放 application-local.yml（.gitignore）；打包时 pom 已排除该文件（maven-jar-plugin）；
      生产走 application-prod.yml 的环境变量占位（FOX_DASHSCOPE_API_KEY 等，见 .env.example）
```

### 2. Spring AI 1.0 → 1.1.x API 改了
| 教程 1.0.x | 项目 1.1.x |
|---|---|
| `CallAroundAdvisor` | `CallAdvisor` |
| `aroundCall` | `adviseCall` |
| `chain.nextAroundCall()` | `chain.nextCall()` |
| `new InMemoryChatMemory()` | `MessageWindowChatMemory.builder()...build()` |
| `FunctionCallback` | `ToolCallback` |
| `.functions("name")` | `.tools(toolObject)` |

照搬教程编译报红，先查这张表。

### 3. mvnw wrapper jar 经常被 .gitignore
`./mvnw` 报"找不到主类"——99% 是 `.mvn/wrapper/maven-wrapper.jar` 没提交到 git。  
**解法：用 IDEA 的 Maven 面板，不碰命令行**。

### 4. @Component Bean 启动时就会调 API
被 `@Component` 注解的 Bean，Spring 启动时构造方法会执行。如果构造里 `new ChatClient(...)` 然后直接 `.call().content()`，**启动就会烧一次 API 调用**。  
**解法：要么别在构造里调，要么用 `@PostConstruct` 标记真实初始化逻辑**。

---

## 🌐 部署（微信云托管 / Docker / Serverless）

生产用 `prod` profile（`application-prod.yml`）：**无密钥、默认不连 MCP / 不连数据库**，单容器即可启动。

```bash
# 1. 打 fat jar（jar 内不含 application-local.yml，密钥不会被打进去）
mvn clean package -DskipTests
# 产物：target/Fox-ai-agent-0.0.1-SNAPSHOT.jar

# 2. 启动（环境变量按 .env.example 注入）
export SPRING_PROFILES_ACTIVE=prod
export FOX_DASHSCOPE_API_KEY=sk-你的key      # 必填
export FOX_SEARCH_API_KEY=xxx                # 可选，联网搜索用
java -jar Fox-ai-agent-0.0.1-SNAPSHOT.jar --server.port=80
```

三种部署姿势：

- **微信云托管 / 任意 Docker 环境**：直接使用根目录 `Dockerfile`（多阶段构建，平台拉代码即可构建）。
  控制台配置：端口 `80`、健康检查 `/api/doc.html`、环境变量 `SPRING_PROFILES_ACTIVE=prod` + `FOX_*`。
- **阿里云函数计算 FC**：上传 fat jar，启动命令 `java -jar /code/app.jar`，环境变量同上。
- **本地一键开发**：`start-dev.cmd`（MCP 图片搜索 8127 + 主应用 8123 + 前端 5173）。

**生产环境能力边界（当前默认配置）**：
- 对话 / 流式 / FoxManus 联网搜索：✅（需要 `FOX_DASHSCOPE_API_KEY`）
- 图片搜索 MCP / 本地 RAG 向量库 / PostgreSQL：默认关闭（单容器不引入外部依赖），
  需要时按 `application-prod.yml` 注释放开并填对应 `FOX_*` 环境变量

**为什么默认不连 Redis / PG？**
- 单实例 + 低并发，文件版 Kryo 记忆够用（部署在容器里注意挂载持久化目录）
- demo 项目不引入生产复杂度，按需开启

---

## 📊 百宝箱进度（持续更新）

| 能力 | 状态 | 完成时间 | 文档 |
|---|---|---|---|
| 多轮对话 + 持久化记忆 | ✅ | 8-31 | `学习笔记/Agent笔记8-31.md` |
| RAG 知识库基础 | ✅ | 9-1 | `学习笔记/[9-1] RAG知识库基础·笔记.md` |
| RAG 进阶（ETL/查询重写/混合检索） | ✅ | 9-2 | `学习笔记/[9.2] RAG进阶·笔记.md` |
| 工具调用（Function Calling） | ✅ | 9-3 | `学习笔记/Agent笔记9-3-工具调用.md` |
| 全能智能体 FoxManus（ReAct + 工具） | ✅ | 9-9 | - |
| 流式输出（token 级 SSE） | ✅ | 9-9 | - |
| MCP 协议（图片搜索） | 🔶 | 9-9 | `fox-image-search-mcp-server` |
| 饮食健康助手重构（提示词 + RAG 库） | ✅ | 9-9 | - |
| 多模态（图片） | 📋 | - | - |
| 多 Agent 协作 | 📋 | - | - |

---

## 🙏 致谢

- **教程**：鱼皮《AI 超级智能体实战》—— 课程主线 + 代码骨架来源
- **框架**：[Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) —— 让 Spring Boot 集成 DashScope 如此自然
- **模型**：[阿里云百炼](https://help.aliyun.com/zh/model-studio) —— 通义千问 / 通义万相 API 服务
- **灵感**：本项目 README 结构参考开源社区"awesome-xxx"系列仓库的写法

---

## License

MIT License - 仅用于学习目的，请勿用于商业场景。