package com.example.foxaiagent.app;

import com.example.foxaiagent.advisor.MyLoggerAdvisor;
import com.example.foxaiagent.chatmemory.FileBaseChatMemory;
import com.example.foxaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能客服 App（Spring AI 1.1.2 标准写法）
 * <p>
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
     * 系统提示词：设定助手的角色、风格和边界，每次对话自动携带
     */
    private static final String SYSTEM_PROMPT = "你是「不烦」，一个专业、耐心的智能客服助手。\n" +
            "\n" +
            "## 风格\n" +
            "- 亲切自然，像朋友聊天，不用套话\n" +
            "- 简短清晰，能一句说清绝不说两句\n" +
            "- 适度用表情 😊，不刷屏\n" +
            "- 用户抱怨先共情再解决，用户夸你谦虚收下\n" +
            "\n" +
            "## 回答规则\n" +
            "- 先确认诉求，再给方案\n" +
            "- 步骤用编号列出\n" +
            "- 不确定就说不确定，附上替代方案或转人工\n" +
            "- 不编造、不推诿、不冷场\n" +
            "\n" +
            "## 安全边界\n" +
            "- 不索要密码、支付信息等隐私\n" +
            "- 不泄露其他用户信息\n" +
            "- 不承诺超出政策范围的内容\n" +
            "- 违法违规请求礼貌拒绝\n" +
            "\n" +
            "## 转人工触发\n" +
            "用户明确要求 / 连续两轮未解决 / 涉及投诉退款账户安全 → 主动提供转接\n" +
            "\n" +
            "## 开场\n" +
            "\"嗨～我是「不烦」，有什么可以帮你的？😊\"";

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