# Spring AI 学习手记 · 博客系列大纲（技术向 · 脱敏版）

> 系列名（建议）：**《Java 后端 × Spring AI：从 0 到智能体框架的学习手记》**
> 定位：纯技术分享。不出现课程名 / 具体项目名 / 代码仓库地址 / 作者隐私信息。
> 更新节奏建议：每周 1~2 篇。
>
> 写作红线（发每篇前自查）：
> - ❌ 不提任何培训机构、付费课程、up 主名字
> - ❌ 不放个人/学习项目仓库地址
> - ❌ 不贴学习笔记里的"进度式"内容（视频第几集、第几天）
> - ✅ 只讲通用技术 + 自己实现的思路 + 可复现的代码片段

---

## 📌 系列总览（建议发布顺序）

| # | 篇名（CSDN 标题） | 主题 | 核心内容 | 建议篇幅 |
|---|---|---|---|---|
| 0 | 预备：AI 应用开发环境搭建 & 大模型三种接入方式对比 | 接入 | DashScope SDK / Spring AI / LangChain4j | ✅ 已有草稿待脱敏 |
| 1 | Spring AI 智能客服实战：从单轮到多轮记忆 | 记忆 | ChatClient / ChatMemory / Advisor / 持久化 | 2500~4000 字 |
| 2 | 给 AI 应用接个"知识库"：RAG 检索增强实战 | RAG | RAG 四步流 / VectorStore / 本地 vs 云端 | 3000~4500 字 |
| 3 | RAG 进阶：查询重写、多查询与向量库选型 | RAG 进阶 | QueryRewriter / MultiQuery / PgVector | 3500~5000 字 |
| 4 | 让 Agent 真正"动手"：工具调用（Function Calling）实战 | 工具 | @Tool / 六大工具 / 注册机制 | 3000~4500 字 |
| 5 | Agent 的外挂：MCP 协议与图片搜索 Server 实战 | MCP | MCP / Server / Tool / Client | 3000~4500 字 |
| 6 | 手写一个 ReAct/ToolCall 分层智能体框架 | 框架 | BaseAgent / ReAct / ToolCall / 状态机 | 4000~5500 字 |
| 7 | 复盘总结：Spring AI 应用开发，校招/简历怎么讲 | 求职 | 简历写法 / 面试题清单（通用视角） | 2000~3000 字 |

**系列钩子**：每篇结尾埋"下一篇"悬念 + 互动问题，引导评论区。

---

## 第 0 篇 · 预备：环境搭建 & 大模型三种接入方式对比

**CSDN 标题**：AI 应用开发环境搭建 & 大模型三种接入方式对比（DashScope SDK / Spring AI / LangChain4j）
**摘要**：搭建 AI 应用开发环境踩过的坑整理：JDK 21、Maven、DashScope API Key 配置与防泄漏；用真实代码对比 DashScope SDK、Spring AI Alibaba、LangChain4j 三种接入方式的代码量 / 学习曲线 / 与 Spring 生态的契合度，附选型建议。
**标签**：Spring AI、通义千问、大模型、Java、Spring Boot

**正文骨架**：
1. 环境搭建（工具清单表 + JDK 21 + API Key + Key 防泄漏最佳实践）
2. 三种接入方式（各附代码 + 架构图）
3. 五维对比表
4. 选型建议（校招 / 生产视角）
5. 踩坑清单 4 条

---

## 第 1 篇 · Spring AI 智能客服实战：从单轮到多轮记忆

**CSDN 标题**：Spring AI 实战：打造带记忆的智能客服（ChatClient + ChatMemory + Advisor）
**摘要**：从单轮问答到"记住上下文"的多轮对话。拆解 ChatClient 组装、MessageChatMemoryAdvisor 记忆机制、会话隔离（chatId），并分享把记忆落盘为文件的实现思路——应用重启后历史不丢。
**标签**：Spring AI、ChatMemory、多轮对话、Java、AI应用

**正文骨架**：
1. 需求：为什么对话必须"有记忆"（对比图）
2. 第一版：ChatClient 单轮问答（代码）
3. 升级：Advisor 链 + ChatMemory（代码 + 架构图）
4. 深入：会话隔离 chatId 怎么工作
5. 进阶：文件版持久化记忆（覆盖写丢历史的 bug 复盘）
6. 实测：三轮对话 + 重启不丢
7. 易错点表

---

## 第 2 篇 · 给 AI 应用接个"知识库"：RAG 检索增强实战

**CSDN 标题**：Spring AI RAG 实战：给 AI 应用接入企业知识库（本地向量库 + 云端知识库）
**摘要**：模型答错业务政策 = 事故。用真实客服场景从 0 实现 RAG：文档加载 → 切分 → Embedding → 入库，再到检索增强问答，本地 SimpleVectorStore 与云端知识库两套方案对照，附检索日志排查技巧。
**标签**：Spring AI、RAG、向量数据库、知识库、大模型

**正文骨架**：
1. 场景：为什么需要 RAG（幻觉 vs 知识库）
2. RAG 四步工作流（架构图）
3. 本地版：ETL 链路代码（加载 / 切分 / 入库）
4. 云端版：托管知识库接入
5. 测试：检索命中日志怎么看
6. 易错点：Embedding 不一致召回为 0 / 索引名 404

---

## 第 3 篇 · RAG 进阶：查询重写、多查询与向量库选型

**CSDN 标题**：RAG 进阶实战：查询重写、多查询扩展与 PgVector（告别"检索不到"）
**摘要**：RAG 检索不准的 80% 原因是查询太烂。本文实现查询重写（改写口语化问题）、多查询扩展（一题多查）、关键词增强，并把向量库从内存升级到 PgVector 持久化，附相似度阈值与空上下文兜底策略。
**标签**：RAG、PgVector、Spring AI、检索增强、向量数据库

**正文骨架**：
1. 进阶前的问题清单（recall 低 / 噪声多 / 空上下文）
2. 预检索三种：重写 / 压缩 / 多查询（表）
3. QueryRewriter 实现（代码）
4. MultiQueryExpander + 关键词增强（代码）
5. PgVector：从内存到持久化（代码 + docker-compose）
6. 空上下文兜底策略
7. 踩坑清单

---

## 第 4 篇 · 让 Agent 真正"动手"：工具调用（Function Calling）实战

**CSDN 标题**：Spring AI 工具调用实战：让大模型真正"干活"（搜索/抓网页/下载/跑终端/读写文件/生成PDF）
**摘要**：LLM 自己啥也干不了，工具调用让它能联网、能跑终端、能操作文件。从 @Tool 注解讲起，实现 WebSearch、WebScraping、ResourceDownload、TerminalOperation、FileOperation、PDFGeneration 六大工具，梳理统一注册机制与调用执行流程。
**标签**：Spring AI、Function Calling、工具调用、Agent、大模型

**正文骨架**：
1. 为什么需要工具（"帮我查下今天新闻" → 幻觉 vs 真搜索）
2. 工具调用 4 步流程（图）
3. @Tool 最小示例（代码）
4. 六大工具逐个拆解（各附代码 + 场景）
5. 工具的注册与发现机制
6. 安全提醒（终端/文件类工具的双刃剑）
7. 踩坑：工具描述写不清 / 工具抛异常 / 参数过多

---

## 第 5 篇 · Agent 的外挂：MCP 协议与图片搜索 Server 实战

**CSDN 标题**：MCP 实战：给 Agent 接上"远程外挂"（手写一个图片搜索 MCP Server）
**摘要**：工具是本地技能，MCP 是通用协议——一个 Server 能被任何 MCP 客户端复用。本文手写一个图片搜索 MCP Server 子模块，再让主应用以远程 MCP 方式调用，讲清 MCP 的 Server / Tool / Client 三层与协议价值。
**标签**：MCP、Model Context Protocol、Spring AI、AI Agent

**正文骨架**：
1. MCP 是什么（vs 本地工具 对比表）
2. MCP 架构：Server / Tool / Client（图）
3. 子模块实现：Tool 定义 + 配置类（代码）
4. 主应用接入远程 MCP（配置 + 调用链）
5. 实测：让 Agent 搜索图片
6. 生态盘点：MCP 为什么是 Agent 标配

---

## 第 6 篇 · 手写一个 ReAct/ToolCall 分层智能体框架

**CSDN 标题**：从 0 手写智能体框架：ReAct + ToolCall 分层设计
**摘要**：不满足于"调 ChatClient"，从 BaseAgent 抽象开始，设计 ReActAgent（思考-行动-观察循环）与 ToolCallAgent（原生函数调用）两条路线，落地 AgentState 状态机，最终组装成一个可扩展的 Agent 基座。附框架类图与"为什么要分层"的思考。
**标签**：Agent框架、ReAct、ToolCall、Spring AI、大模型应用架构

**正文骨架**：
1. 需求：多个智能体能力如何复用
2. 框架设计：抽象基类 → 两条实现路线（类图）
3. AgentState：智能体的生命周期状态
4. ReAct 循环实现（Thought / Action / Observation）
5. ToolCall 路线实现（原生 tool_calls）
6. 两条路线对比与取舍
7. 实测效果
8. 复盘：分层带来的可扩展性

---

## 第 7 篇 · 复盘总结：Spring AI 应用开发，简历/面试怎么讲

**CSDN 标题**：从 0 到智能体框架：Spring AI 学习复盘与面试指南
**摘要**：技术讲完了，聊聊更现实的事。复盘一条可复制的 Spring AI 学习路线（对话→记忆→RAG→工具→MCP→框架），总结简历项目写法、高频面试题与话术要点，以及踩过的坑。通用视角，不绑定任何课程。
**标签**：Spring AI、Java后端、面试、简历、AI应用

**正文骨架**：
1. 学习路线图复盘（表：阶段 → 产出 → 可写进简历的能力）
2. 简历项目描述 3 版（100 / 200 / 400 字）
3. Spring AI 高频面试题清单
4. 加分话术 vs 减分话术
5. 学习节奏建议
6. 系列索引（所有文章链接）

---

## 📎 配套素材建议（每篇通用）

- **代码块**：用通用命名，去掉个人项目包结构痕迹（`com.example` 即可）
- **架构图**：每篇 1~2 张（draw.io 导出 PNG 或 ASCII）
- **封面**：CSDN 自带 AI 配图，不用代码截图当封面
- **开头模板**："大家好，我是正在学 Spring AI 的 Java 后端开发。今天聊聊 XX 的实现思路……"
- **结尾模板**："完整代码思路和踩坑点都写在上文了，下篇写 XX，欢迎关注。"

---

## ⏰ 发布排期建议（以周为粒度）

| 周 | 发什么 | 状态 |
|---|---|---|
| 本周 | 第 0 篇（已有草稿，改标题摘要 + 去个人痕迹即可发） | 🟡 待脱敏 |
| 第 1 周 | 第 1 篇 + 第 2 篇 | 未写 |
| 第 2 周 | 第 3 篇 + 第 4 篇 | 未写 |
| 第 3 周 | 第 5 篇 + 第 6 篇 | 未写 |
| 第 4 周 | 第 7 篇复盘 | 未写 |

**动力机制**：周更 2 篇 = 每周 2 个 deadline。
