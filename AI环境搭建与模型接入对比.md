# AI 应用开发环境搭建 & 大模型三种接入方式对比

> 跟着鱼皮《AI 超级智能体实战》做项目时，我把自己踩过的坑整理成文。这篇不讲理论，纯粹是从"零搭环境到能跑通 AI 对话"的实操记录，三种接入方式都附代码片段和我的真实对比感受。

---

## 一、环境搭建（Windows 11）

### 1.1 工具清单

| 工具 | 版本 | 作用 |
|---|---|---|
| JDK | Corretto 21.0.12 | Spring Boot 3.x 强制要求 |
| Maven | IDEA 自带 | 项目构建 |
| IDEA | 2023.1+ | 主力 IDE |
| DashScope API Key | 阿里云百炼申请 | 调通义千问必需 |

### 1.2 JDK 安装

Spring Boot 3.5.10 最低要求 JDK 17，但教程里大量用到 `var`、虚拟线程、record 模式等 21 特性，**直接上 21**。

推荐 **Amazon Corretto 21**（OpenJDK 发行版，免费、稳定、阿里云兼容性好）：

```bash
# 1. 下载：https://aws.amazon.com/corretto/
# 2. 解压到 C:\Users\xxx\.jdks\corretto-21.0.12.1\
# 3. IDEA 里 File → Project Structure → SDKs → + 选这个目录
```

### 1.3 Maven

不用单独装 Maven，**用 IDEA 自带的**就行。如果非要用 CLI，记住 Spring Boot 项目结构用 `./mvnw` 而不是 `mvn`（mvnw 会自动下载项目指定的 Maven 版本，团队协作时版本统一）。

> ⚠️ 我自己的坑：`.mvn/wrapper/maven-wrapper.jar` 这个文件**经常在 .gitignore 里被忽略**，结果就是 mvnw 命令报"找不到类"。解决办法：要么把 wrapper jar 也提交到 git，要么干脆用 IDEA 的 Maven 面板，不碰命令行。

### 1.4 IDEA 必备插件

| 插件 | 用途 |
|---|---|
| Lombok | `@Slf4j` / `@Data` 必备 |
| MyBatisX | 调试 SQL 时用（本期用不到） |
| Rainbow Brackets | 嵌套括号多了眼睛会瞎 |
| GitToolBox | 看每行代码最后提交者 |

### 1.5 DashScope API Key

去 https://dashscope.aliyun.com/apiKey 申请，**每个阿里云账号有免费额度**（百万 tokens 级别），学习完全够用。

**关键**：Key 千万别提交到 git。我把真实 Key 放在 `application-local.yml`，这个文件已经在 `.gitignore` 里：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${ALI_AI_KEY:sk-你的真实key填这里}
```

`application.yml` 只放公共配置，敏感 Key 走环境变量或者 `application-local.yml`，这样即使代码被推到公网也不会泄漏。

---

## 二、三种接入方式（亲测可用）

我的项目 pom 里**同时引入了三种依赖**，原因是教程分别演示了三种调用方式，下面逐一对比。

```xml
<!-- 方式 A：原生 SDK -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.22.31</version>
</dependency>

<!-- 方式 B：Spring AI Alibaba 体系 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
    <version>1.1.2.0</version>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.1.2.0</version>
</dependency>

<!-- 方式 C：LangChain4j -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

### 2.1 方式 A：DashScope 原生 SDK（直接 HttpClient）

**定位**：最贴近 HTTP 协议，无框架抽象。

```java
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;

import java.util.Arrays;

public class SdkAiInvoke {
    public static String invoke(String userMessage) {
        try {
            Generation gen = new Generation();
            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content("你是一个有帮助的助手")
                    .build();
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(userMessage)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .model("qwen-plus")  // 通义千问 plus
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .topP(0.8)
                    .apiKey(System.getenv("ALI_AI_KEY"))
                    .build();

            GenerationResult result = gen.call(param);
            return JsonUtils.toJson(result);
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException("DashScope 调用失败", e);
        }
    }
}
```

**特点**：
- 优点：完全控制请求细节（temperature、top_p、tools 等参数都能精确调）
- 缺点：写 30 行代码才能完成一次基础对话；没有 ChatMemory、自动重试、工具调用这些"开箱即用"的能力
- 适用：底层调试、学习协议、做协议封装层

### 2.2 方式 B：Spring AI Alibaba（我主力使用）

**定位**：Spring 生态的标准做法。**框架抽象 + 自动装配**。

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringAiAiInvoke {

    private final ChatClient chatClient;

    public SpringAiAiInvoke(ChatModel dashscopeModel) {
        // 内存版 ChatMemory（生产换 Redis / 文件）
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        chatClient = ChatClient.builder(dashscopeModel)
                .defaultSystem("你是「不烦」，一个专业、耐心的智能客服助手")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String chat(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }
}
```

**配置文件**（`application-local.yml`）：
```yaml
spring:
  ai:
    dashscope:
      api-key: ${ALI_AI_KEY:sk-xxx}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
```

**特点**：
- 优点：`ChatClient` 封装了所有 HTTP 细节；`Advisor` 链可以横切插入记忆、日志、RAG、工具调用；和 Spring Boot 项目天然融合
- 缺点：要学 Spring AI 抽象（`ChatModel` / `ChatClient` / `Advisor`），新概念不少
- 适用：**Spring Boot 项目首选**，尤其是要做记忆、RAG、工具调用等复合场景

**Spring AI 1.1.x 注意点**：
- `ChatMemory` 的 API 改了：`new InMemoryChatMemory()` 已经删除，要用 `MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).maxMessages(10).build()`
- 老的 `CallAroundAdvisor` → 新的 `CallAdvisor`，方法名 `aroundCall` → `adviseCall`
- 很多教程（基于 1.0.x）照搬到 1.1.x 会编译报错，**跟着 1.1.x 的文档/源码走**别照搬旧教程

### 2.3 方式 C：LangChain4j（社区活跃）

**定位**：Java 生态最成熟的 LLM 框架之一，社区驱动。

```java
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;

public class LangChainAiInvoke {

    public static String invoke(String userMessage) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(System.getenv("ALI_AI_KEY"))
                .modelName("qwen-plus")
                .temperature(0.7)
                .build();

        ChatResponse response = model.chat(UserMessage.from(userMessage));
        return response.aiMessage().text();
    }
}
```

**特点**：
- 优点：API 设计直观；社区生态丰富（工具链、向量库、Agent 框架齐全）；跨模型切换方便
- 缺点：不直接和 Spring 集成（虽然有 `langchain4j-spring-boot-starter`，但和 Spring AI 的契合度不如 Spring AI Alibaba）；文档质量参差
- 适用：想用 LangChain 完整生态（LangSmith、LangGraph），或者非 Spring 项目

---

## 三、横向对比

### 3.1 五个维度对比

| 维度 | DashScope 原生 SDK | Spring AI Alibaba | LangChain4j |
|---|---|---|---|
| **代码量（一次对话）** | 30+ 行 | 10 行 | 8 行 |
| **学习曲线** | 低（会 Java 即可） | 中（要学 Spring AI 抽象） | 中（要学 LangChain4j 概念） |
| **和 Spring Boot 集成** | 需自己封装 | **原生** | 需额外 starter |
| **ChatMemory / RAG / Tools** | 全部手写 | **开箱即用** | 开箱即用 |
| **多模型切换成本** | 改每个调用点 | 改 yml 一个字段 | 改 builder 一个字段 |
| **国内社区活跃度** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **中文文档** | 官方中文 | 官方中文 + 鱼皮教程 | 英文为主 |
| **Debug 友好度** | 高（直接看 HTTP） | 中（框架封装多层） | 中 |

### 3.2 关键差异图示

```
                    ┌─────────────────────┐
                    │   你的 Java 代码    │
                    └──────────┬──────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       │                       │                       │
       ▼                       ▼                       ▼
  ┌─────────┐         ┌──────────────┐         ┌─────────────┐
  │ SDK 直接 │         │ Spring AI    │         │ LangChain4j │
  │ HttpClient│        │ Alibaba      │         │             │
  └────┬────┘         └──────┬───────┘         └──────┬──────┘
       │                     │                       │
       │  HTTP              │ HTTP                  │ HTTP
       ▼                     ▼                       ▼
  ┌─────────────────────────────────────────────────────────┐
  │          DashScope 阿里云 API 平台                        │
  │   qwen-plus / qwen-max / text-embedding-v3 / qwen-vl    │
  └─────────────────────────────────────────────────────────┘
```

三种方式最后都打到同一个 API 平台，**区别只在客户端框架**。

---

## 四、我的选型建议

### 4.1 校招 / 简历项目

**强烈推荐 Spring AI Alibaba（方式 B）**，理由：

1. **契合度最高**：你既然用 Spring Boot 做后端项目，Spring AI 是最自然的选择
2. **面试官熟**：Spring 是 Java 面试必考，Spring AI 是新加分项
3. **教程资源多**：鱼皮《AI 超级智能体实战》、编程导航、知网、知乎大量 Spring AI 中文资料
4. **企业实际在用**：阿里内部、滴滴、字节多个团队都在用 Spring AI 体系

### 4.2 学习路径建议

```
第 1 步：用 Spring AI Alibaba 跑通对话（1-2 天）
        ↓
第 2 步：加 ChatMemory（多轮对话），自己实现持久化（Kryo/Redis）
        ↓
第 3 步：加 QuestionAnswerAdvisor（RAG 知识库问答）
        ↓
第 4 步：加 Function Calling（工具调用）
        ↓
第 5 步：加流式输出（SSE，前后端对接）
        ↓
第 6 步：上 MCP（Model Context Protocol，对接外部工具生态）
```

### 4.3 什么时候用 LangChain4j

- 你想用 LangGraph（多 Agent 工作流）
- 你用非 Spring 框架（Vert.x、Quarkus）
- 你要做跨语言复用（LangChain4j 有 Kotlin 版本）

### 4.4 什么时候直接用 SDK

- 你要做的功能非常简单（一次问答 + 自定义 UI）
- 你想深入理解 HTTP 协议和模型参数
- 你要做底层封装（比如自己写框架）

---

## 五、我踩过的坑（节省你 3 小时）

### 5.1 配置文件 Key 泄漏

```
错：把 sk-xxxx 直接写在 application.yml 然后 push 到 gitee
对：application.yml 写 ${ALI_AI_KEY:sk-默认}，真实 Key 放 application-local.yml（已 .gitignore）
```

### 5.2 Spring AI 版本差异

教程版本和当前版本 API 不一致，**编译报红先怀疑是版本问题**：

| 教程 1.0.x | 项目 1.1.x |
|---|---|
| `CallAroundAdvisor` | `CallAdvisor` |
| `aroundCall` | `adviseCall` |
| `chain.nextAroundCall()` | `chain.nextCall()` |
| `new InMemoryChatMemory()` | `MessageWindowChatMemory.builder()...build()` |
| `ChatMemory.CONVERSATION_ID` | 同一个名（这个没变，但 IDE 索引问题偶尔会找不到） |

### 5.3 mvnw wrapper jar 丢失

`./mvnw` 报"找不到主类"——99% 是 `.mvn/wrapper/maven-wrapper.jar` 没提交到 git。**用 IDEA 的 Maven 面板**绕过这个问题，别死磕命令行。

### 5.4 每次启动烧一次 API 调用

注意：被 `@Component` 注解的 Bean，Spring 启动时会执行构造方法。如果你写了 `new ChatClient(...)` 然后构造里直接 `.call().content()`，**那一次也会真的调 API**。解决办法：要么别在构造里调，要么用 `@PostConstruct` 标记真正需要初始化的逻辑。

---

## 六、最后一句话

**不要纠结"哪种最好"，先用 Spring AI Alibaba 跑通第一个 demo，再回头看其他两种方式，会突然理解它们存在的意义**。AI 框架的抽象层级差异，只有用过才能体会。

祝你也早日跑通第一个 AI 对话 😊

---

> 本文基于 Fox-ai-agent 项目实践整理，源码在 gitee.com/maguawing/fox-ai-agent
> 配套视频教程：鱼皮《AI 超级智能体实战》
