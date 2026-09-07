# Spring AI RAG 进阶实战：查询重写、多查询扩展与 PgVector（告别"检索不到"）

> **CSDN 发布信息**（复制用）：
> 摘要：RAG 检索不准的 80% 原因是"查询太烂"，不是知识库不好。本文实战三个进阶武器：查询重写（QueryRewriter 把口语化问题改写成检索友好句式）、多查询扩展（Multi-Query 一题多查合并召回）、向量库从内存升级到 PgVector 持久化，附检索质量诊断与调优清单。
> 标签：RAG、PgVector、Spring AI、检索增强、向量数据库
> 分类：AI 应用开发 / Spring AI

---

## 一、进阶前的问题清单

上一篇的 RAG 能答对"退货几天"，但生产场景很快暴露新问题：

```
用户问："我不想要了咋退？还能不能退运费？"

❌ 基础 RAG 的表现：
- 直接拿这句口语去向量检索 → 召回结果乱七八糟
- "咋退" 和知识库里的 "退货流程" 语义相近但字面差很远
- 向量库里存的是文档的原文表述，用户说话用的是另一套话术
- 结果：要么召回错政策，要么召回为空
```

**核心认知**：RAG 检索不准的 80% 原因不是知识库不好，而是**用户查询质量太差**。

| 典型病句 | 病在哪 |
|---|---|
| "我不想要了咋退" | 口语化，跟文档表述对不上 |
| "那运费谁出？" | 有指代"那"，脱离上下文无法检索 |
| "你们这能退东西吗" | 太泛，一个 query 打不到多个政策 |
| 英文/日文问中文知识库 | 语言不一致，向量空间对不上 |

进阶思路就一句话：**把"用户的原话"做手术，变成"适合检索的几句话"再去查**。

---

## 二、三件进阶武器总览

| 武器 | 解决什么 | 属于检索哪一段 |
|---|---|---|
| **查询重写 QueryRewriter** | 口语 → 书面、模糊 → 明确 | 预检索 |
| **多查询扩展 Multi-Query** | 一个问题拆 N 个角度，召回更全 | 预检索 |
| **PgVector 持久化** | 向量库从内存 → 数据库，重启不丢、可扩展 | 存储层 |

```
用户问题
   │
   ▼
┌─────────────────────────────┐
│ 预检索（查询手术）             │
│ ① 查询重写：口语 → 书面        │
│ ② 多查询扩展：1 题 → N 题      │
└──────────────┬──────────────┘
               ▼
        向量库检索（topK×N）
               │ 合并去重
               ▼
        拼 prompt → 模型回答
```

---

## 三、查询重写（QueryRewriter）

### 3.1 思路

让大模型**自己**把用户口语改写成检索友好的表述，再拿去向量检索：

```
输入：  "我不想要了咋退？还能不能退运费？"
输出：  "退货流程是怎样的？7天无理由退货运费由谁承担？质量问题退货运费由谁承担？"
```

### 3.2 实现

```java
@Component
public class QueryRewriter {

    private final ChatClient chatClient;

    public QueryRewriter(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /** 把用户口语问题改写为适合向量检索的书面问题 */
    public String doQueryRewriter(String userQuestion) {
        return chatClient.prompt()
                .system("你是查询改写助手。把用户的问题改写成适合向量数据库检索的书面表达。"
                        + "要求：① 消除口语化；② 若包含代词(它/这个/那)，结合常识补全成完整表述；"
                        + "③ 若有多个疑问点，拆成多个独立问句；④ 只输出改写结果，不要解释。")
                .user(userQuestion)
                .call()
                .content();
    }
}
```

### 3.3 接入检索链路

```java
public String ragChat(String message, String chatId) {
    // ① 先用查询改写"洗"一遍用户问题
    String rewritten = queryRewriter.doQueryRewriter(message);
    log.info("查询改写：{} → {}", message, rewritten);

    // ② 拿改写后的问题去 RAG
    return chatClient.prompt()
            .user(rewritten)                      // ⬅️ 用改写版，不是原文
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
            .advisors(ragAdvisor)
            .call()
            .content();
}
```

> ⚠️ **代价提醒**：查询改写本身也是一次 LLM 调用，每轮问答会多花一次 token。可以在命中率差的场景按需开启，不必每次都走。

---

## 四、多查询扩展（Multi-Query Expansion）

### 4.1 思路

一个用户问题往往覆盖多个知识角度，单次检索容易漏。让模型把问题拆成 N 个不同角度的子查询，**分别检索再合并**：

```
用户： "我要退货，麻烦说下流程"
  ↓ 拆成
  Q1: "退货申请的入口和步骤是什么？"
  Q2: "7天无理由退货的条件和范围？"
  Q3: "退货运费由谁承担？"
  Q4: "退款到账需要多久？"
  ↓ 分别检索 topK 后合并去重
   → 覆盖更全，不容易漏政策
```

### 4.2 实现

```java
public class MultiQueryExpander {

    private final ChatClient chatClient;

    public MultiQueryExpander(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /** 把一个问题扩展成 N 个检索子查询 */
    public List<String> expandQuery(String query) {
        String json = chatClient.prompt()
                .system("你是检索查询扩展助手。把用户问题拆成 3~5 个不同角度的子查询，"
                        + "覆盖：流程类、条件类、费用类、时效类。返回 JSON 数组，如 [\"q1\",\"q2\"]，只输出数组。")
                .user(query)
                .call()
                .content();

        // 解析 JSON 数组（用 Jackson / 手写都行），返回 List<String>
        return parseJsonArray(json);
    }
}
```

### 4.3 合并策略（RRF 倒数排名融合）

多路查询各自召回 topK 后，需要**合并排序**。业界常用 RRF（Reciprocal Rank Fusion）：

```
RRF_score(doc) = Σ  1 / (k + rank_i(doc))     k 通常取 60
```

含义：某文档在多路结果里名次越靠前，融合分越高；k 的作用是压平榜首优势，让"每路都排中等"的文档也能冒头。

```java
// 伪代码：多路结果合并
Map<String, Double> score = new HashMap<>();
for (List<Document> hits : allQueryResults) {
    for (int i = 0; i < hits.size(); i++) {
        String id = hits.get(i).getId();
        score.merge(id, 1.0 / (60 + i), Double::sum);   // RRF
    }
}
// 按 score 降序取 topK 拼 prompt
```

> 很多向量库和云知识库内置了多路检索合并，不一定手写；理解原理即可，生产上优先用现成能力。

---

## 五、向量库升级：PgVector 持久化

### 5.1 为什么升级

| | SimpleVectorStore（内存/文件） | PgVector（PostgreSQL 插件） |
|---|---|---|
| 存储位置 | 本地文件 | **数据库表** |
| 多实例共享 | ❌ 各自一份 | ✅ 同一张表 |
| 重启 | load 缓存文件 | 天然在库里 |
| 与业务数据联合查询 | 不能 | ✅ 可以和订单表 SQL JOIN |
| 适合 | 学习 / 单机 demo | 生产 / 要和其他数据关联 |

### 5.2 准备 PostgreSQL + 插件

```bash
# Docker 起一个带 vector 插件的 PostgreSQL
docker run -d --name pgvector \
  -e POSTGRES_USER=user -e POSTGRES_PASSWORD=pass \
  -e POSTGRES_DB=rag_db \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# 建扩展（首次连上后执行一次）
CREATE EXTENSION IF NOT EXISTS vector;
```

### 5.3 Spring AI 接入

依赖引入（pom.xml）：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    <version>1.1.2</version>
</dependency>
```

配置类：
```java
@Configuration
public class PgVectorStoreConfig {

    @Bean
    public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                     EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("knowledge_chunks")   // 自定义表名
                .schemaName("public")
                .dimensions(1536)                      // 对齐你的 Embedding 维度
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .build();
    }
}
```

> ⚠️ **踩坑预告**：`dimensions` 必须和你 Embedding 模型的输出维度一致（DashScope text-embedding-v3 默认 1024，v2 是 1536），对不上建表会报错。

### 5.4 应用代码零改动

`PgVectorStore` 和 `SimpleVectorStore` 都实现 `VectorStore` 接口，所以上一篇的 `QuestionAnswerAdvisor` 代码**一行不用改**，只是注入的 Bean 换了：

```java
// 之前：注入 SimpleVectorStore
// 现在：注入 PgVectorStore（Bean 名 vectorStore）
@Resource
private VectorStore vectorStore;   // 接口类型，底层实现随便换
```

这就是接口抽象的又一次胜利——存储层替换，业务无感。

---

## 六、检索质量诊断清单

怎么知道检索到底行不行？三板斧：

**① 看日志命中分布**（上一篇讲过）：
```
命中 3 条：退货政策 ✓ 会员制度 ✓ FAQ ✓
```
命中条数 ≥2 且来源相关 → 正常；命中 0 或全是无关文档 → 有问题。

**② 单测用例设计**（易混词测试）：
```java
@Test
void 易混政策测试() {
    // "换货" 应该命中 02-换货政策，而不是 01-退货政策
    List<Document> hits = vectorStore.similaritySearch(
        SearchRequest.builder().query("想换个颜色，怎么申请换货").topK(3).build());
    assertTrue(hits.get(0).getMetadata().get("filename").toString()
        .contains("02-换货"));
}
```
> 知识库设计时**故意埋几组易混问题**（退货 7 天 vs 换货 30 天 vs 大家电 15 天），是检验检索质量最高效的方法。

**③ 兜底观察**：检索为空时，模型是否按 system prompt 说"不知道"，而不是硬编。

---

## 七、调优清单（从能用 → 好用）

| 症状 | 原因 | 对策 |
|---|---|---|
| 检索召回空 / 很少 | 查询太口语 | 上**查询重写** |
| 召回漏了某个角度 | 单查询覆盖不全 | 上**多查询扩展** |
| 召回的片段顺序不对 | 只按相似度排 | 上 **RRF 融合 / 重排模型** |
| 专有名词查不到（订单号） | 向量检索对精确串不敏感 | 向量 + 关键词混合检索（BM25） |
| 命中一堆无关文档 | 阈值太低 | 设 `similarityThreshold`（0.6~0.7） |
| 重启丢失 / 多实例不共享 | 内存向量库 | 换 **PgVector** |
| Embedding 维度对不上 | dimensions 配置错 | 查模型文档确认维度 |

---

## 八、面试怎么讲

被问到"RAG 检索不准怎么优化"：

> 我按三层递进优化：第一层是查询侧——用户口语直接检索召回很差，我实现了查询重写器，用大模型把"我不想要了咋退"改写成"退货流程与退货运费规则"再检索，命中率明显提升；第二层是召回侧——单查询容易漏角度，我实现了多查询扩展，把问题拆成流程/条件/费用/时效四个子查询分别检索，再用 RRF 倒数排名融合合并去重；第三层是存储侧——把向量库从内存 SimpleVectorStore 升级到 PgVector 持久化，重启不丢、多实例共享，应用代码因为依赖 VectorStore 接口而零改动。

**加分追问预备**：
- "查询改写每次多一次 LLM 调用，成本怎么控？" → "只在高频且命中率低的场景开启，或加开关按需调用；也可以用更便宜的小模型做改写。"
- "为什么用 PgVector 而不是专用向量库？" → "项目已经有 PostgreSQL，PgVector 是插件零额外组件；而且能和业务表做 SQL 联合查询。数据量大再考虑 Milvus 这类专用库。"

---

## 九、下篇预告

知识库能查准了，但客服还是只能"动嘴"。下一篇讲 **工具调用（Function Calling）**：让 AI 真正"动手"——联网搜索、抓网页、跑终端、读写文件、生成 PDF。

欢迎留言聊聊：你的 RAG 检索命中率多少？遇到最头疼的是"查不到"还是"查不准"？
