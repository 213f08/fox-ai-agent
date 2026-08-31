package com.example.foxaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
}
