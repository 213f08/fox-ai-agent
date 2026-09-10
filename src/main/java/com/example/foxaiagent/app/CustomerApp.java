package com.example.foxaiagent.app;

import cn.hutool.json.JSONUtil;
import com.example.foxaiagent.advisor.MyLoggerAdvisor;
import com.example.foxaiagent.chatmemory.FileBaseChatMemory;
import com.example.foxaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 饮食健康助手 App（Spring AI 1.1.2 标准写法）
 * <p>
 * 由「智能客服」改版而来：人设、系统提示词与本地 RAG 知识库(document/*.md)
 * 整体替换为饮食健康主题（减脂/增肌/控糖/血压/痛风/维生素/误区FAQ）。
 * 对应教程旧版 API 的改写：
 * <ul>
 *   <li>new InMemoryChatMemory()            → MessageWindowChatMemory + InMemoryChatMemoryRepository</li>
 *   <li>CHAT_MEMORY_CONVERSATION_ID_KEY     → ChatMemory.CONVERSATION_ID（值相同，仅改名）</li>
 *   <li>CHAT_MEMORY_RETRIEVE_SIZE_KEY = 10  → 构造记忆时 maxMessages(10)，运行时不再传</li>
 *   <li>chatResponse.getResult().getOutput().getText() → call().content()</li>
 * </ul>
 */
@Component
@Slf4j
public class CustomerApp {
    /**
     * 仅本地 profile 有 VectorStore bean（CustomerAppVectorStoreConfig 标注了 @Profile("!prod")），
     * prod 走百炼云端知识库，用不到本地向量库 → required=false，生产不因缺 bean 启动失败。
     */
    @Autowired(required = false)
    private VectorStore pgVectorStore;
    private final ChatClient chatClient;
    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 知识库检索的相似度下限（余弦相似度，0~1）。
     * <p>
     * 不设会吃 Spring AI 的默认值 0.0 —— 那意味着「任何文档都算命中」，
     * 随便一句「哦豁」「哈哈」都会被塞进 4 篇最相近的文档当参考资料，
     * 前端还会显示「完成 4 个过程步骤」，看起来像无意义问题也走了 RAG。
     * <p>
     * 0.6 是本地实测定标：真实饮食问题 0.71~0.75，闲聊/跑题 0.48~0.55，取中间留足余量。
     * 觉得仍然误命中就往上调（0.65），觉得该命中没命中就往下调（0.5 左右）。
     * 调参时看日志 [小养RAG] 检索命中 N 篇，阈值 x，最高分 y 里的「最高分」。
     */
    @org.springframework.beans.factory.annotation.Value("${customer-app.rag.similarity-threshold:0.6}")
    private double ragSimilarityThreshold;

    @org.springframework.beans.factory.annotation.Value("${customer-app.rag.top-k:4}")
    private int ragTopK;
    /**
     * 系统提示词：设定助手的角色、风格和边界，每次对话自动携带。
     * 角色定位是「饮食健康助手」——不是医生，不诊断、不替代医嘱；
     * 涉及疾病请提示线下就医。知识细节优先引用 RAG 检索结果(document/*.md)。
     */
    private static final String SYSTEM_PROMPT = "你是「小养」，一个懂营养、会做饭的饮食健康助手，帮用户把一日三餐吃得更健康。\n" +
            "\n" +
            "## 定位\n" +
            "- 擅长：减脂、增肌、控糖、高血压/痛风等慢病膳食、维生素矿物质、饮食误区辟谣\n" +
            "- 你不是医生，不诊断疾病、不开药；涉及明确疾病请建议线下就医或咨询注册营养师\n" +
            "\n" +
            "## 风格\n" +
            "- 亲切自然，像身边懂营养的朋友，不用套话\n" +
            "- 简短清晰、给可执行的建议：能说具体数字/份量就说具体（如\"每餐一拳头主食\"）\n" +
            "- 适度用表情 😊，不刷屏\n" +
            "- 用户焦虑先安抚，再给方案\n" +
            "\n" +
            "## 回答规则\n" +
            "- 先确认诉求（减脂？控糖？只是好奇？）再展开，避免答非所问\n" +
            "- 知识库有依据时优先引用知识库结论（退货/售后等无关问题直接说明不是本助手范围）\n" +
            "- 不确定的营养数据不编造，明确说\"这块证据还不充分\"或建议查阅权威指南\n" +
            "- 涉及药物（降压药/降糖药等）与饮食相互作用时，强调遵医嘱\n" +
            "- 不推荐极端方案（断食减肥、单一食物减肥、代餐当饭吃）\n" +
            "\n" +
            "## 安全边界\n" +
            "- 急症、疑似中毒、过敏反应等：立即建议就医，不隔空处理\n" +
            "- 孕妇/儿童/老人/肾病等特殊人群：饮食建议更加谨慎，倾向线下咨询\n" +
            "- 不索要病史细节用于\"开方\"，不做疾病诊断\n" +
            "\n" +
            "## 开场\n" +
            "\"嗨～我是「小养」，饮食健康小助手 😊 想聊减脂、控糖、怎么吃更健康都可以问我！\"";

    /**
     * 启动时由 Spring 调用（仅一次）：
     * ChatModel 是 spring-ai-alibaba-starter-dashscope 读取 application-local.yml 配置后
     * 自动装配进容器的 Bean，这里注入并组装成对话入口 ChatClient
     *
     * @param dashscopeModel DashScope 自动装配的模型 Bean
     */
    public CustomerApp(ChatModel dashscopeModel) {
        // 对话记忆：自定义 FileBaseChatMemory（Kryo 序列化到 .kryo 文件），
        // 存在 user.dir/tmp/chat-memory 下，应用重启后历史不丢；
        // 单会话最多保留 maxMessages(10) 条（对应教程的 RETRIEVE_SIZE=10）
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";

        ChatMemory chatMemory = new FileBaseChatMemory(fileDir, 10);

        // 组装对话入口：系统提示词全局生效，记忆由 Advisor 在每轮对话前后自动读写
        chatClient = ChatClient.builder(dashscopeModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()
                            ,new MyLoggerAdvisor()
//                            ,new ReReadingAdvisor()
                    )
                .build();
    }

    /**
     * 多轮对话：同一个 chatId 共享一份记忆，不同 chatId 完全隔离
     *
     * @param message 用户输入
     * @param chatId  会话 ID
     * @return 模型回复内容
     */
    public String doChat(String message, String chatId) {
        String content = chatClient.prompt()
                .user(message)
                // 指定本次对话属于哪个会话（教程旧常量 CHAT_MEMORY_CONVERSATION_ID_KEY 的现名）
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
        log.info("content: {}", content);
        return content;
    }
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                // 指定本次对话属于哪个会话（教程旧常量 CHAT_MEMORY_CONVERSATION_ID_KEY 的现名）
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();

    }
    /**
     * 结构化输出：让模型按固定 JSON 结构返回服务报告
     * 字段名即模型要返回的 JSON key，Jackson 会自动反序列化
     * public 修饰，供 controller 层跨包返回该结构
     */
    public record CustomerReport(String title, List<String> services) {
    }

    /**
     * 多轮对话 + 服务报告：同一个 chatId 共享一份记忆，不同 chatId 完全隔离
     * 返回结构化对象（不是纯文本），由 .entity() 完成 JSON → Java 对象的转换
     *
     * @param message 用户输入
     * @param chatId  会话 ID
     * @return 结构化的服务报告对象
     */
    public CustomerReport doChatWithReport(String message, String chatId) {
        CustomerReport report = chatClient.prompt()
                .user(message)
                // 注意：.system() 会覆盖 defaultSystem，所以把 SYSTEM_PROMPT 拼回来；
                // 并明确要求只输出 JSON，字段名必须与 CustomerReport 的字段一致（title / services）
                .system(SYSTEM_PROMPT
                        + "\n每次对话结束都要生成一份服务报告，使用 JSON 格式返回，"
                        + "字段为 title（报告标题，形如「xxx 的服务报告」）"
                        + "和 services（本次的服务列表，字符串数组）。只返回 JSON，不要额外解释。")
                // 指定本次对话属于哪个会话（教程旧常量 CHAT_MEMORY_CONVERSATION_ID_KEY 的现名）
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(CustomerReport.class);
        log.info("report: {}", report);
        return report;
    }
    @Autowired(required = false)
    @Qualifier("customerAppVectorStore")
    private VectorStore customerAppVectorStore;

    /**
     * 云知识库检索增强顾问（由 CustomerAppCloudAdvisorConfig 注入）
     */
    @Autowired(required = false)
    @Qualifier("customerAppRagCloudAdvisor")
    private Advisor customerAppRagCloudAdvisor;

    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewritten = queryRewriter.doQueryRewriter(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 云端知识库 RAG；只需本地时注释此行，并取消下一行注释
                .advisors(customerAppRagCloudAdvisor)
                // .advisors(QuestionAnswerAdvisor.builder(pgVectorStore).build())
                //文档检索增强
//                .advisors(
//                        CustomerAppRagCustomerAdvisorFactory.creatCustomerAppRagCustomerAdvisor(
//                            customerAppVectorStore, "物流"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 饮食健康助手「小养」的流式问答（SSE）：
     * - 本地开发（local）：基于本地饮食健康知识库做 RAG 检索增强（QueryRewrite + QuestionAnswerAdvisor）
     * - 生产（prod）：本地向量库不存在（@Profile("!prod")），自动退化为纯对话流式，不报错
     *
     * 前端 diet 模式走该接口：GET /api/ai/diet/chat/sse?message=&chatId=
     */
    public Flux<String> doDietRagChatStream(String message, String chatId) {
        if (customerAppVectorStore != null) {
            // 查询重写（把口语化问题改写成适合检索的表述），再走本地知识库检索增强
            String rewritten = queryRewriter.doQueryRewriter(message);
            log.info("[小养RAG] 查询重写：{} → {}", message, rewritten);

            // ① 检索本地知识库，把命中结果作为「可见过程」先推给前端（type=rag）
            //    先用阈值 0 取回 topK 条（SimpleVectorStore 按分数降序），拿到真实最高分，
            //    再按阈值自己过滤 —— 这样日志里的分数才对调参有意义。
            //    （若直接把 threshold 传给 SearchRequest，没命中的话一份文档都拿不到，
            //     日志只能打 0.0000，看不出"差多少才命中"。）
            List<Document> candidates = customerAppVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(rewritten)
                            .topK(ragTopK)
                            .similarityThreshold(0.0)
                            .build());
            // getScore() 是包装类型，理论上可能为 null（自定义 VectorStore 不回填分数），
            // 直接拆箱会 NPE，这里统一兜底成 0。
            double topScore = candidates.stream()
                    .map(Document::getScore)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0d);
            List<Document> docs = candidates.stream()
                    .filter(d -> d.getScore() != null && d.getScore() >= ragSimilarityThreshold)
                    .toList();
            // SLF4J 只认 {} 占位，不支持 {:.4f} 这类格式化语法，小数位自己截
            log.info("[小养RAG] 检索命中 {} 篇，阈值 {}，最高分 {}",
                    docs.size(), ragSimilarityThreshold, String.format("%.4f", topScore));

            // 无一条过阈值 → 判定为闲聊 / 超出知识库范围，不拼资料、不发 rag 事件，
            // 直接走纯对话，前端也就不会显示「过程步骤」。
            if (docs.isEmpty()) {
                log.info("[小养RAG] 未命中（最高分 {} < 阈值 {}），退化为纯对话", topScore, ragSimilarityThreshold);
                return plainChatStream(message, chatId);
            }

            List<String> ragEvents = new ArrayList<>();
            StringBuilder context = new StringBuilder();
            int idx = 0;
            for (Document doc : docs) {
                idx++;
                String filename = String.valueOf(doc.getMetadata().getOrDefault("filename", "饮食知识"));
                String snippet = doc.getText() == null ? "" : doc.getText();
                if (snippet.length() > 160) snippet = snippet.substring(0, 160) + "…";
                ragEvents.add(streamEvent("rag", "📄 " + filename.replace(".md", "") + "\n" + snippet));
                context.append("【资料").append(idx).append("｜").append(filename).append("】\n").append(doc.getText()).append("\n\n");
            }

            // ② 参考资料拼进 system（保留小养人设，QA 内容优先于模型先验知识），正文流式返回
            Flux<String> answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT
                            + "\n\n【本地知识库检索到的参考资料，请优先依据资料回答，资料不足时再结合你的知识并说明】\n"
                            + context)
                    .user(rewritten)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content()
                    .map(token -> streamEvent("answer", token));
            return Flux.concat(Flux.fromIterable(ragEvents), answer);
        }
        // 生产无本地向量库：纯对话流式（JSON 事件包装，前端解析一致）
        return plainChatStream(message, chatId);
    }

    /** 不带知识库资料的纯对话流式（小养人设 + 多轮记忆依然生效） */
    private Flux<String> plainChatStream(String message, String chatId) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(token -> streamEvent("answer", token));
    }

    /**
     * 组装流式事件 JSON：{"type":"rag|answer","content":"..."}，前端据此区分
     * 「参考的知识库过程」与「最终回答」。
     */
    private static String streamEvent(String type, String content) {
        Map<String, String> event = new HashMap<>();
        event.put("type", type);
        event.put("content", content);
        return JSONUtil.toJsonStr(event);
    }
    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        String answer = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // allTools 已是 ToolCallback[]，须用 .toolCallbacks() 而非 .tools()
                .toolCallbacks(allTools)
                .call()
                .content();
        log.info("answer: {}", answer);
        return answer;
    }
    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;
    public String doChatWithMCP(String message, String chatId) {
        String answer = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // allTools 已是 ToolCallback[]，须用 .toolCallbacks() 而非 .tools()
                .toolCallbacks(toolCallbackProvider)
                .call()
                .content();
        log.info("answer: {}", answer);
        return answer;
    }
}