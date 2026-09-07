# Spring AI RAG 实战：给 AI 应用接个"知识库"（本地向量库 + 云端知识库）

> **CSDN 发布信息**（复制用）：
> 摘要：模型答错业务政策 = 事故。用真实客服场景从 0 实现 RAG：文档加载 → 切分 → Embedding → 入库，再到检索增强问答，本地 SimpleVectorStore 与云端知识库两套方案对照，附"首次初始化 vs 后续加载"的启动机制拆解与检索日志排查技巧。
> 标签：Spring AI、RAG、向量数据库、知识库、大模型
> 分类：AI 应用开发 / Spring AI

---

## 一、场景：为什么需要 RAG

上篇文章给客服接上了记忆，它能记住对话。但业务问题照样答不了：

```
我：你好，退货期限是几天？
AI：您好，一般商品自签收之日起 7 天内可以无理由退货。
我：那运费谁出？
AI：运费由买家承担。（❌ 瞎编——其实 7 天无理由退货运费买家出，质量问题退货运费卖家出）
```

**问题在哪**：模型没有你公司的售后政策数据，只能靠"常识"编。这就是大模型的三大原罪：

| 原罪 | 表现 |
|---|---|
| **不知道私有数据** | 你的公司政策它根本没学过 |
| **知识有截止时间** | 2024 年后改的政策它不知道 |
| **幻觉** | 不知道也硬答，还答得理直气壮 |

> **RAG（Retrieval-Augmented Generation，检索增强生成）** 就是解法：模型回答前，**先去知识库检索相关资料**，把资料拼进提示词，再生成答案。相当于"开卷考试带资料"——不背进脑子，而是现查。

---

## 二、RAG 四步工作流

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  ① 文档   │ →  │  ② 向量   │ →  │  ③ 检索   │ →  │  ④ 增强   │
│  收集切割  │    │  转换存储  │    │  相似召回  │    │  拼 prompt│
└──────────┘    └──────────┘    └──────────┘    └──────────┘
  Reader +         Embedding       similarity       模型基于
  Splitter         模型 + 向量库    Search(topK)     资料回答
```

**① 文档收集切割**：把售后政策、FAQ 等写成 Markdown/PDF，按语义切成小块（500~1000 字/块）
**② 向量转换存储**：用 Embedding 模型把每块文本变成高维向量，连原文一起存进向量数据库
**③ 检索**：用户提问也转成向量，在库中算相似度，召回最相关的 topK 块
**④ 增强**：把召回片段拼进 prompt："参考以下资料回答…"，模型只依据资料生成

---

## 三、本地版：ETL 链路代码

### 3.1 准备知识文档

我建了一个 `document/` 目录放客服知识，纯 Markdown、标题清晰，方便切分后语义完整：

```
src/main/resources/document/
├── 01-退货退款政策.md    # 7 天无理由 / 运费承担
├── 02-换货与维修政策.md  # 30 天换货 / 12 个月保修
├── 03-物流配送说明.md    # 发货时效 / 包邮规则
├── 04-会员制度.md        # 普通/超级会员
├── 05-发票开具.md        # 电子普票 / 企业专票
└── 06-常见问题FAQ.md     # 高频问答速查
```

> **写作心法**：一条政策写成一句完整的话（不依赖上下文代词），这样无论怎么切都不会"腰斩"关键信息。

### 3.2 文档加载器（Extract）

```java
@Component
public class DocumentLoader {

    private final ResourcePatternResolver resolver;

    public DocumentLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> all = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withAdditionalMetadata("filename", filename)  // 元数据：来源文件名
                        .build();
                all.addAll(new MarkdownDocumentReader(resource, config).get());
            }
        } catch (IOException e) {
            throw new RuntimeException("知识文档加载失败", e);
        }
        return all;
    }
}
```

> `withAdditionalMetadata("filename", filename)` 很关键——每个 chunk 都会带上"来自哪个文件"的元数据，后面过滤检索和排查问题都靠它。

### 3.3 向量库装配（Transform + Load + Query）

```java
@Configuration
public class VectorStoreConfig {

    private static final File CACHE_FILE =
            new File(System.getProperty("user.dir") + "/tmp", "vector-store.json");

    @Bean
    public VectorStore customerVectorStore(
            EmbeddingModel embeddingModel,
            DocumentLoader loader) {

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // ① 有缓存：直接加载，跳过重复向量化（首次 35s → 二次 5s 的关键）
        if (CACHE_FILE.exists()) {
            store.load(CACHE_FILE);
            log.info("从缓存加载向量库：{}（跳过向量化）", CACHE_FILE.getPath());
            return store;
        }

        // ② 无缓存：读文档 → 入库 → 落盘
        List<Document> docs = loader.loadMarkdowns();
        store.add(docs);                      // 内部自动调 EmbeddingModel 向量化
        store.save(CACHE_FILE);               // 落盘，下次启动直接 load
        log.info("首次初始化完成，向量库已缓存到：{}", CACHE_FILE.getPath());
        return store;
    }
}
```

### 3.4 第一次初始化 vs 后续加载（本篇重点）

上面这段代码藏着 RAG 落地的**一个关键机制**——向量化是有"一次性成本"的，不该每次启动都付：

```
第 1 次启动（导入知识库后）：
  读文档 → Embedding 向量化（43 份文档 = 43 次 API 调用）→ 入库 → save 落盘
  ⏱ 约 30~40 秒（大头是逐片调 Embedding API）

第 2 次启动及以后：
  发现缓存文件存在 → load(File) 直接读回 → 跳过全部 Embedding 调用
  ⏱ 约 5 秒，且不烧 API 额度
```

**为什么要区分这两步？**

| 维度 | 首次初始化 | 后续加载 |
|---|---|---|
| 做了什么 | 读文档 + Embedding + 入库 + 落盘 | 从本地文件反序列化读回 |
| 耗时 | 30~40 秒（网络请求逐片向量化） | 2~5 秒（纯本地 IO） |
| API 调用 | 每个 chunk 一次 embedding（花钱） | **0 次** |
| 什么时候跑 | 知识库首次建好 / 文档更新后删缓存 | 每次应用启动 |

**底层原理**：`SimpleVectorStore` 序列化时把**向量也一起写进了 JSON**（不只是文本）。所以 `load()` 时向量是现成的，根本不需要重新调 Embedding——向量库文件 `vector-store.json` 里存的就是"文本 + 高维向量 + 元数据"的完整数据。

**日常使用的心智模型**：

```
知识库建好（首次）  ──→  之后每次启动 = 打开文件（快）
文档改了           ──→  删掉缓存文件 → 重新走首次流程（让改动生效）
```

> ⚠️ **最容易踩的坑**：改完知识文档忘了删缓存 → 应用启动很快（走 load 分支）→ 但检索结果还是旧文档 → 你以为改了没生效。**改文档后必须删 `tmp/vector-store.json`**（代码注释里也建议写上这句）。

### 3.5 检索增强接入（doChatWithRag）

```java
@Component
public class ChatAssistant {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatAssistant(ChatModel model, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(model)
                .defaultSystem("你是专业、耐心的智能客服。回答必须基于提供的参考资料，资料没有的信息明确说不知道。")
                .build();
    }

    public String ragChat(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(vectorStore,   // ⬅️ RAG 核心
                        SearchRequest.builder().topK(5).build()))
                .call()
                .content();
    }
}
```

**改动就一处**：多挂一个 `QuestionAnswerAdvisor`，检索 + 拼 prompt + 基于资料回答全自动完成。

**验证**：

```
我：退货期限是几天？
AI：根据我们的退货政策，自商品签收之日起 7 天内支持无理由退货（特殊商品除外）。需要我帮您确认具体商品吗？
```

——不再瞎编，答案有据可依了。

---

## 四、云端版：托管知识库接入

本地版有个痛点：**SimpleVectorStore 存内存/本地文件**，不适合多实例部署。生产上更省事的做法是用**云厂商的托管知识库**（阿里云百炼 / AWS Bedrock 等），文档上传由控制台管理，你只负责接 API。

### 4.1 云控制台准备

1. 登录百炼控制台，创建**知识库**
2. 把 `document/` 的文档**上传到知识库**（云端自动完成切分 + 向量化）
3. 记下**知识库名称**（索引名，代码里要用，必须一字不差）

### 4.2 接入代码

```java
@Configuration
public class CloudRagConfig {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${rag.cloud.knowledge-index}")
    private String knowledgeIndex;   // = 控制台知识库名称，如 "customer-service-kb"

    @Bean
    public QuestionAnswerAdvisor cloudRagAdvisor() {
        // 云端文档检索器：按知识库名从百炼检索
        DocumentRetriever retriever = new DashScopeDocumentRetriever(apiKey, knowledgeIndex);
        return new QuestionAnswerAdvisor(retriever,
                SearchRequest.builder().topK(5).build());
    }
}
```

然后 `.advisors(cloudRagAdvisor)` 挂上即可，调用方代码和本地版**完全一样**——`QuestionAnswerAdvisor` 屏蔽了底层差异，这是接口抽象的威力。

> 💡 **本地 vs 云端在"初始化"上的差别**：本地版需要你自己管理"首次向量化 + 缓存复用"；云端版把这个成本**转移到了控制台**——文档上传时云端就完成切分向量化，应用每次启动只是连接远程检索，天然没有"重复向量化"问题。

---

## 五、测试：检索命中日志怎么看

RAG 最怕"**以为在检索，其实没命中**"。建议在自定义 Advisor 里打日志看每次检索命中什么：

```java
// 命中片段存在 ChatResponse 的 metadata 里
@SuppressWarnings("unchecked")
List<Document> hits = (List<Document>) response.getMetadata()
        .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
if (hits == null || hits.isEmpty()) {
    log.warn("[RAG] 检索为空！可能：知识库没数据 / Embedding 模型不一致 / topK 太小");
} else {
    log.info("[RAG] 命中 {} 条：", hits.size());
    hits.forEach(doc -> log.info("  · {} | {}",
            doc.getMetadata().get("filename"), summarize(doc.getText())));
}
```

**日志里该看到的样子**：

```
[RAG] 命中 3 条：
  · 01-退货退款政策.md | 自商品签收之日起 7 天内支持无理由退货，运费由买家承担…
  · 04-会员制度.md     | 超级会员享每月 3 次免费上门取件退货…
  · 06-常见问题FAQ.md  | 问：退货和换货哪个快？答：换货更快…
```

看到 `filename` 来源、命中条数、内容预览 → 检索链路就是通的。

---

## 六、易错点速查表

| 坑 | 原因 | 排查/修复 |
|---|---|---|
| **召回为 0** | 入库和检索用的 **Embedding 模型不一致**（两个不同的 embedding 向量空间） | 全程用同一个 EmbeddingModel Bean |
| **检索到但答案仍瞎编** | topK 太小没召回对 / system prompt 没约束"必须基于资料" | 调大 topK（5~10），prompt 写死"资料没有就说不知道" |
| **云端报 Index not found / 404** | 知识库名称与配置不一致 | 代码里的索引名 = 控制台知识库名，一字不差 |
| **改了文档但检索结果没变** | 缓存文件没删，走的是 load 分支 | 文档更新后**删除 `tmp/vector-store.json`** 重新初始化 |
| **重启后检索全空** | 本地 SimpleVectorStore 没落盘 | `store.save(File)` 落盘，启动时 `load(File)` |
| **切分后语义断裂** | chunk 太小或太大 | 500~1000 字/块，保留 50~200 字重叠 |
| **命中一堆无关片段** | 没做召回后的相关性过滤 | 设 `similarityThreshold`（如 0.6），低分块不进 prompt |

---

## 七、面试怎么讲

被问到"项目里 RAG 怎么实现的"：

> 我先用 Spring AI 的 ETL 链路把客服知识文档读进来：MarkdownDocumentReader 解析并给每个 chunk 打上"来源文件名"元数据，Embedding 模型向量化后存入 SimpleVectorStore。这里我特意做了**首次初始化与后续加载的分离**：向量化是有一次性的 API 成本（几十次 embedding 调用），所以首次入库后把带向量的 store 序列化到本地文件，之后每次启动直接 load 复用，启动时间从 35 秒降到 5 秒、零 API 消耗。问答时挂 QuestionAnswerAdvisor，每次请求自动检索 topK=5 个相关片段拼进 prompt，并要求模型必须基于资料回答。同时对比验证了本地 SimpleVectorStore 和云端托管知识库两套方案。

**加分追问预备**：
- "缓存失效怎么处理？" → "文档更新后删掉缓存文件，下次启动自动重新走初始化流程。生产上可以按文档 hash 判断是否需要重建。"
- "召回不准怎么办？" → "三个方向：查询改写提升命中、加相似度阈值过滤低分块、上重排模型。下一篇会讲。"

---

## 八、下篇预告

基础版 RAG 能答了，但一问"退货要几天"就可能召回错政策（"换货 30 天"和"退货 7 天"太像）。下一篇讲 **RAG 进阶：查询重写、多查询扩展与检索质量调优**——解决"检索不到"和"检索不准"。

欢迎留言聊聊：你的 AI 应用接知识库了吗？用的是本地向量库还是云托管？
