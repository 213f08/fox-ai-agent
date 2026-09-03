# Fox AI Agent · 智能体百宝箱

<div align="center">

**一个 Spring AI 学习驱动的"智能体能力百宝箱"项目**

每个 `App` 是一个独立能力槽位：客服对话 · RAG 检索 · 工具调用 · 服务报告 ……  
跟着鱼皮《AI 超级智能体实战》打底，在每个槽位里塞进真实业务场景练习。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2-blue)](https://github.com/alibaba/spring-ai-alibaba)
[![DashScope](https://img.shields.io/badge/DashScope-qwen--plus-ff6a00)](https://help.aliyun.com/zh/model-studio)
[![License](https://img.shields.io/badge/license-MIT-green)](#license)

</div>

---

## 📖 项目定位

> **不是**线上可用的客服系统，**是**用 Spring AI 把 AI 应用核心能力做一遍的学习型"百宝箱"。

- **每个 App 一个能力**：可独立 demo / 测试 / 拼装组合
- **每个能力对应面试一个考点**：RAG / 工具调用 / 持久化 / 结构化输出 / 多 Agent
- **可部署到 Serverless**：阿里云函数计算（FC）原生支持 Spring Boot Jar，一键上云

---

## 🧰 百宝箱能力清单

| App / 模块 | 能力 | 状态 | 入口 |
|---|---|---|---|
| `CustomerApp「不烦」` | 多轮对话 + 文件持久化记忆（Kryo 序列化） | ✅ 已完成 | `com.example.foxaiagent.app.CustomerApp` |
| `CustomerApp` | RAG 知识库问答（文档检索增强） | ✅ 已完成 | `doChatWithRag` 方法 |
| `CustomerApp` | 结构化输出（服务报告 JSON） | ✅ 已完成 | `doChatWithReport` 方法 |
| `CustomerApp` | 自定义 Advisor（日志 / ReReading） | ✅ 已完成 | `com.example.foxaiagent.Advisor.*` |
| `EchoTools` / `OrderTools` | 工具调用（Function Calling） | 📋 计划中 | `doChatWithTools` 方法（待加） |
| `StreamingChatApp` | 流式输出（SSE） | 📋 计划中 | - |
| `MultimodalApp` | 多模态（图片理解） | 📋 计划中 | - |
| `McpApp` | MCP 协议对接 | 📋 计划中 | - |
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

# 3. 启动（IDEA 里直接跑 FoxAiAgentApplication 也行）
./mvnw spring-boot:run

# 4. 访问
#    接口：http://localhost:8123/api
#    文档：http://localhost:8123/api/doc.html（Knife4j）
```

### 第一次调用

```bash
# 多轮对话
curl "http://localhost:8123/api/ai/chat?msg=你好&chatId=test1"

# RAG 检索问答（依赖 knowledge-base 文档）
curl "http://localhost:8123/api/ai/rag?msg=退货几天？&chatId=test2"
```

---

## 📚 知识库（麻瓜优选 RAG Demo）

虚构电商品牌「麻瓜优选」的客服知识文档，作为 RAG 能力的检索源：

```
src/main/resources/document/
├── 01-退货退款政策.md     # 7天无理由 / 运费承担
├── 02-换货与维修政策.md   # 30天换货 / 12个月保修
├── 03-物流配送说明.md     # 发货时效 / 包邮规则
├── 04-会员制度与收费.md   # 普通/超级会员 / 月卡年卡
├── 05-发票开具流程.md     # 电子普票 / 企业专票
├── 06-账号与安全.md       # 注册 / 密码 / 注销
└── 07-常见问题FAQ.md      # 高频问题速查
```

**写作要点**：标题清晰、条目自包含（切分友好），刻意埋了检索陷阱（退货 7 天 vs 换货 30 天 vs 大家电 15 天），可作为 RAG 精度测试用例。

---

## 🛠️ 技术栈

| 类别 | 技术 | 版本 | 作用 |
|---|---|---|---|
| **核心框架** | Spring Boot | 3.5.10 | Web 容器 / Bean 管理 |
| **JDK** | Amazon Corretto | 21 | 虚拟线程 / Record Pattern |
| **AI 框架** | Spring AI Alibaba | 1.1.2.0 | Agent 编排 / ChatClient / Advisor |
| **AI 模型** | DashScope | qwen-plus / qwen-max | 阿里云百炼大模型 |
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
├── Advisor/                       # 自定义 Advisor
│   ├── MyLoggerAdvisor.java       # 请求/响应日志埋点
│   └── ReReadingAdvisor.java      # Re2 提升推理质量
├── app/
│   └── CustomerApp.java           # 主应用：「不烦」智能客服
├── chatmemory/
│   ├── FileBaseChatMemory.java    # 自定义 Kryo 文件版 ChatMemory
│   └── KryoMemoryViewer.java      # 调试工具：查看 .kryo 文件内容
├── rag/                           # RAG 模块（百宝箱能力）
│   ├── CustomerAppDocumentLoader.java  # Markdown 文档加载
│   └── CustomerAppVectorStoreConfig.java  # 向量库装配
└── demo/invoke/                   # 四种调用方式 Demo
    ├── SdkAiInvoke.java           # DashScope SDK
    ├── HttpAiInvoke.java          # 原生 HTTP
    ├── SpringAiAiInvoke.java      # Spring AI（主力）
    └── LangChainAiInvoke.java     # LangChain4j

src/main/resources/
├── application.yml                # 公共配置
├── application-local.yml          # 本地密钥（gitignored）
└── document/                      # 知识库文档
```

---

## 🎓 学习路线

按这个顺序看代码，1 周就能掌握 Spring AI 核心：

| 阶段 | 章节 | 关键概念 | 对应代码 |
|---|---|---|---|
| ① | LLM 接入 | ChatModel / ChatClient | `SpringAiAiInvoke` |
| ② | 多轮对话 | ChatMemory + Advisor | `CustomerApp.doChat` |
| ③ | 自定义记忆 | 实现 `ChatMemory` 接口 | `FileBaseChatMemory` |
| ④ | RAG 基础 | Document / Embedding / VectorStore | `CustomerApp.doChatWithRag` |
| ⑤ | 工具调用 | `@Tool` / `.tools()` | （待加 `doChatWithTools`） |
| ⑥ | 流式输出 | SSE / Flux | （待加） |
| ⑦ | 多模态 | qwen-vl / 图片问答 | （待加） |
| ⑧ | MCP | Model Context Protocol | （待加） |

---

## ⚠️ 踩坑清单

部署前必看的 4 个坑（每条都有详细解释）：

### 1. API Key 不能进仓库
```
❌ 错：application.yml 直接写 sk-xxx，push 到 gitee 后密钥泄漏
✅ 对：application.yml 写 ${ALI_AI_KEY}，真实 Key 放 application-local.yml（已 .gitignore）
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

## 🌐 部署（Serverless / 函数计算 FC）

本项目对 FC 部署原生友好（Spring Boot fat jar）：

```bash
# 1. 打 fat jar
./mvnw clean package -DskipTests
# 产物：target/Fox-ai-agent-0.0.1-SNAPSHOT.jar

# 2. FC 控制台创建
#    - 服务名：fox-ai-agent
#    - 函数 runtime：Java 21
#    - 触发器：HTTP 触发
#    - 代码包：上传 jar
#    - 启动命令：java -jar /code/app.jar
#    - 环境变量：ALI_AI_KEY=你的 key

# 3. 拿到公网 URL 即可访问
```

**为什么不上 Redis / 不用 Dockerfile？**
- 单实例 + 低并发，文件版 Kryo 记忆够用
- FC 直接吃 jar，不用容器化
- demo 项目不引入生产复杂度

---

## 📊 百宝箱进度（持续更新）

| 能力 | 状态 | 完成时间 | 文档 |
|---|---|---|---|
| 多轮对话 + 持久化记忆 | ✅ | 8-31 | `学习笔记/Agent笔记8-31.md` |
| RAG 知识库基础 | ✅ | 9-1 | `学习笔记/[9-1] RAG知识库基础·笔记.md` |
| RAG 进阶（ETL/查询重写/混合检索） | ✅ | 9-2 | `学习笔记/[9.2] RAG进阶·笔记.md` |
| 工具调用（Function Calling） | 📋 | - | `学习笔记/Agent笔记9-3-工具调用.md` |
| 流式输出（SSE） | 📋 | - | - |
| 多模态（图片） | 📋 | - | - |
| MCP 协议 | 📋 | - | - |
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