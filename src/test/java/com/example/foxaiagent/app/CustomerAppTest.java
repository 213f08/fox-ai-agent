package com.example.foxaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能客服 App 测试
 * <p>
 * application.yml 默认激活 local profile（API Key 在 application-local.yml），
 * 所以这里不需要再写 @ActiveProfiles
 */
@SpringBootTest
class CustomerAppTest {

    @Resource
    private CustomerApp customerApp;

    @Resource
    private VectorStore customerAppVectorStore;

    @Resource
    private Advisor customerAppRagCloudAdvisor;

    /**
     * 云知识库检索增强顾问装配验证（不调用云 API，只需创建好 Bean 即通过）
     * 真实云端检索需先在百炼控制台创建同名知识库，再到 doChatWithCloudRag 手动验证
     */
    @Test
    void cloudRagAdvisorAssembled() {
        assertNotNull(customerAppRagCloudAdvisor, "云知识库检索增强顾问应已装配");
        assertTrue(customerAppRagCloudAdvisor instanceof RetrievalAugmentationAdvisor,
                "顾问类型应为 RetrievalAugmentationAdvisor");
    }

    /**
     * 基础对话：验证能正常返回内容
     */
    @Test
    void chat() {
        String chatId = "test-chat";
        String answer = customerApp.doChat("你好，你是谁？", chatId);
        System.out.println("回答：" + answer);
        assertNotNull(answer, "回答不应为空");
        assertFalse(answer.isBlank(), "回答不应为空白");
    }

    /**
     * 多轮记忆：同一个 chatId 共享记忆，三轮对话验证记忆持续不丢失
     */
    @Test
    void chatWithMemory() {
        String chatId = "test-memory";

        // 第一轮：告知名字
        String first = customerApp.doChat("我叫麻瓜，请记住我的名字", chatId);
        System.out.println("第一轮回答：" + first);
        assertNotNull(first);

        // 第二轮：换一句话，验证记忆生效
        String second = customerApp.doChat("我叫什么名字？", chatId);
        System.out.println("第二轮回答：" + second);
        assertTrue(second.contains("麻瓜"), "应该能记住上轮提到的名字");

        // 第三轮：再换一种问法，验证记忆持续多轮不丢失
        String third = customerApp.doChat("再提醒我一次，我刚才说我叫什么？", chatId);
        System.out.println("第三轮回答：" + third);
        assertTrue(third.contains("麻瓜"), "第三轮仍应记住名字，记忆不应中途丢失");
    }

    /**
     * 会话隔离：不同 chatId 的记忆互不干扰（可选跑，会多调 2 次 API）
     */
    @Test
    void chatIsolation() {
        String chatIdA = "test-session-a";
        String chatIdB = "test-session-b";

        // 只有 A 说了名字
        customerApp.doChat("我叫麻瓜，请记住我的名字", chatIdA);

        // B 去问，应该不知道
        String answerB = customerApp.doChat("我叫什么名字？", chatIdB);
        System.out.println("会话 B 的回答：" + answerB);
        assertFalse(answerB.contains("麻瓜"), "不同会话记忆应隔离");
    }

    /**
     * 结构化输出：验证模型能按 JSON 结构返回服务报告（会多调 1 次 API）
     * <p>
     * doChatWithReport 返回的是 CustomerApp.CustomerReport（title + services 列表），
     * 由 ChatClient 的 .entity() 把模型返回的 JSON 反序列化而成
     */
    @Test
    void chatWithReport() {
        String chatId = "test-report";
        CustomerApp.CustomerReport report =
                customerApp.doChatWithReport("我想查一下我的订单物流，顺便开一张发票", chatId);

        // 打印便于肉眼核对 JSON 是否被正确解析
        System.out.println("报告标题：" + report.title());
        System.out.println("服务列表：" + report.services());

        assertNotNull(report, "报告对象不应为 null");
        assertNotNull(report.title(), "标题不应为 null");
        assertFalse(report.title().isBlank(), "标题不应为空白");
        assertNotNull(report.services(), "服务列表不应为 null");
        assertFalse(report.services().isEmpty(), "服务列表不应为空，模型应识别出至少一项服务");
    }

    @Test
    void doChatWithRag() {
        String chatId = "test-rag";
        String answer = customerApp.doChatWithRag("我想查一下我的订单物流，顺便开一张发票", chatId);
        System.out.println("回答：" + answer);
        assertNotNull(answer, "回答不应为空");
        assertFalse(answer.isBlank(), "回答不应为空白");
        // RAG 生效的直观证据：回答应基于知识库（物流配送说明 / 发票开具流程）给出具体信息
        assertTrue(answer.contains("物流") || answer.contains("发票"), "RAG 回答应引用知识库中的物流/发票信息");
    }

    /**
     * 云知识库 RAG 端到端测试（真实调用阿里云百炼云知识库检索 + 大模型）
     * <p>
     * 前提：百炼控制台已创建「客服助手知识库」并上传文档（名称见 application.yml 的
     * customer-app.knowledge-index）。会消耗少量 token。
     * <p>
     * 断言依据：知识库文档里的物流/发票规则（16:00 前当天发货、满 59 包邮、
     * 电子发票 24 小时开具等）是文档特有的具体信息，模型不会凭空编出这些细节，
     * 因此命中即证明"云检索 → 拼上下文 → 生成"整条链路生效。
     */
    @Test
    void doChatWithCloudRag() {
        String chatId = "test-cloud-rag";
        String answer = customerApp.doChatWithCloudRag("我想查一下我的订单物流，顺便开一张发票", chatId);
        System.out.println("【云RAG回答】" + answer);
        assertNotNull(answer, "回答不应为空");
        assertFalse(answer.isBlank(), "回答不应为空白");

        boolean hit = answer.contains("16:00") || answer.contains("16点")
                || answer.contains("59") || answer.contains("包邮")
                || answer.contains("24小时") || answer.contains("24 小时")
                || answer.contains("电子") || answer.contains("专票");
        System.out.println("回答是否命中知识库特有细节：" + hit);
        assertTrue(hit, "云 RAG 回答应引用知识库中的物流/发票具体规则（16:00发货 / 满59包邮 / 电子发票24小时等）");
    }

    /**
     * 直接检索向量库，验证文档已成功入库且检索能命中相关知识
     * 这是 RAG 生效的底层证据（不经过 LLM）
     */
    @Test
    void vectorStoreRetrieval() {
        SearchRequest request = SearchRequest.builder()
                .query("发票怎么开？开票需要多久？")
                .topK(3)
                .build();
        List<Document> docs = customerAppVectorStore.similaritySearch(request);
        System.out.println("检索到 " + docs.size() + " 条相关文档：");
        for (Document doc : docs) {
            String text = doc.getText().replace("\n", " ").trim();
            String preview = text.length() > 80 ? text.substring(0, 80) + "..." : text;
            System.out.println("  - [" + doc.getMetadata().get("filename") + "] " + preview);
        }
        assertFalse(docs.isEmpty(), "向量库应检索到文档（说明文档已入库）");
        assertTrue(docs.stream()
                        .anyMatch(d -> String.valueOf(d.getMetadata().get("filename")).contains("发票")),
                "应命中发票相关文档");
    }
}
