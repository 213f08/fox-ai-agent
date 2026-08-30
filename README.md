# Fox-ai-agent

AI 超级智能体实战课程项目：使用 Java 调用阿里云百炼（DashScope）大模型，从 SDK / HTTP / Spring AI / LangChain4j 四种方式逐步演进到 AI Agent 应用。

## 技术栈

- Java 21 + Spring Boot 3.5.10
- 阿里云 DashScope SDK（dashscope-sdk-java）
- Spring AI Alibaba（spring-ai-alibaba-starter-dashscope / agent-framework）
- LangChain4j（langchain4j-community-dashscope）
- Hutool、Lombok、Knife4j（接口文档）

## 快速开始

1. 配置密钥：在 `src/main/resources/application-local.yml` 中填入你的 DashScope API Key（该文件已被 .gitignore 忽略，不会提交）
2. 启动应用：运行 `FoxAiAgentApplication`，服务地址 `http://localhost:8123/api`
3. 接口文档：`http://localhost:8123/api/doc.html`（Knife4j）

## 各调用方式入口（com.example.foxaiagent.demo.invoke）

| 类 | 方式 | 说明 |
|---|---|---|
| `SdkAiInvoke` | DashScope SDK | MultiModalConversation 多模态接口 |
| `HttpAiInvoke` | 原生 HTTP | 多模态 generation 接口 |
| `SpringAiAiInvoke` | Spring AI | ChatModel 自动装配 |
| `LangChainAiInvoke` | LangChain4j | QwenChatModel |

## 注意

- qwen3.8 系列为多模态模型，必须走多模态接口，否则报 `url error`
- langchain4j 1.19.0 起 `ChatLanguageModel` 更名为 `ChatModel`
- `TestApiKey.java` 含密钥，已被 .gitignore 忽略，克隆后需自行创建
