package com.example.foxaiagent.chatmemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileBaseChatMemory 持久化单元测试（不启动 Spring、不调 API）
 * <p>
 * 核心思路：new 一个新的 FileBaseChatMemory 实例 = 模拟应用重启。
 * 只要两个实例读的是同一个目录，历史就应该完整保留。
 * <p>
 * 不用 JUnit 的 @TempDir：本机 TEMP 指向 C:\Windows\Temp 会因权限不足挂掉，
 * 改为在 target/test-tmp 下自建目录并自行清理。
 */
class FileBaseChatMemoryTest {

    private File testDir;

    @BeforeEach
    void setUp() {
        testDir = new File("target/test-tmp/chatmemory-" + System.nanoTime());
        testDir.mkdirs();
    }

    @AfterEach
    void tearDown() {
        // best-effort 清理，失败不影响测试结果
        File[] files = testDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        testDir.delete();
    }

    /**
     * 验证消息可以跨实例（模拟重启）保留
     */
    @Test
    void persistAcrossRestart() {
        String dir = testDir.getAbsolutePath();

        // 第一次运行：写入 2 条消息
        FileBaseChatMemory memory1 = new FileBaseChatMemory(dir, 10);
        memory1.add("chat-1", new UserMessage("我叫麻瓜"));
        memory1.add("chat-1", new UserMessage("今天天气怎么样"));

        // 模拟重启：新建实例读同一个目录
        FileBaseChatMemory memory2 = new FileBaseChatMemory(dir, 10);
        List<Message> messages = memory2.get("chat-1");

        assertEquals(2, messages.size(), "重启后应保留全部 2 条消息");
        assertEquals("我叫麻瓜", messages.get(0).getText());
        assertEquals("今天天气怎么样", messages.get(1).getText());
    }

    /**
     * 验证 saveConversation 是追加而不是覆盖（之前的致命 bug 回归测试）
     */
    @Test
    void appendNotOverwrite() {
        FileBaseChatMemory memory = new FileBaseChatMemory(testDir.getAbsolutePath(), 10);

        // 分两批 add：第二批写完后，第一批必须还在
        memory.add("chat-2", new UserMessage("第一条"));
        memory.add("chat-2", new UserMessage("第二条"));

        List<Message> messages = memory.get("chat-2");
        assertEquals(2, messages.size(), "第二批写入不应覆盖第一批");
    }

    /**
     * 验证滑动窗口：超过 maxMessages 后裁掉最早的消息
     */
    @Test
    void  slidingWindow() {
        FileBaseChatMemory memory = new FileBaseChatMemory(testDir.getAbsolutePath(), 5);

        for (int i = 1; i <= 8; i++) {
            memory.add("chat-3", new UserMessage("消息" + i));
        }

        List<Message> messages = memory.get("chat-3");
        assertEquals(5, messages.size(), "超过窗口上限后应只保留 5 条");
        assertEquals("消息4", messages.get(0).getText(), "最早的消息应被裁掉");
        assertEquals("消息8", messages.get(4).getText(), "最新的消息应保留");
    }

    /**
     * 验证 clear 删除会话文件
     */
    @Test
    void clearRemovesConversation() {
        FileBaseChatMemory memory = new FileBaseChatMemory(testDir.getAbsolutePath(), 10);
        memory.add("chat-4", new UserMessage("要被删掉的消息"));

        memory.clear("chat-4");

        assertTrue(memory.get("chat-4").isEmpty(), "clear 后会话应为空");
    }

    /**
     * 验证不同会话 ID 互相隔离
     */
    @Test
    void conversationIsolation() {
        FileBaseChatMemory memory = new FileBaseChatMemory(testDir.getAbsolutePath(), 10);
        memory.add("chat-a", new UserMessage("A 的消息"));
        memory.add("chat-b", new UserMessage("B 的消息"));

        assertEquals(1, memory.get("chat-a").size());
        assertEquals(1, memory.get("chat-b").size());
        assertEquals("A 的消息", memory.get("chat-a").get(0).getText());
    }
}