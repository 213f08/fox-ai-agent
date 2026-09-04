package com.example.foxaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CustomerAppTest {

    @Resource
    private CustomerApp customerApp;

    @Resource
    private VectorStore customerAppVectorStore;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    // 进程内固定、跨进程随机：避免本地落盘记忆跨运行累积，同时保证同进程内多轮共享同一 chatId
    private static final String RUN_ID = "t" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void chat() {
        String answer = customerApp.doChat("你好，你是谁？", RUN_ID + "-chat");
        System.out.println("回答：" + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void chatWithMemory() {
        String chatId = RUN_ID + "-mem";
        customerApp.doChat("我叫麻瓜，请记住我的名字", chatId);
        String answer = customerApp.doChat("我叫什么名字？", chatId);
        System.out.println("回答：" + answer);
        assertTrue(answer.contains("麻瓜"));
    }

    @Test
    void doChatWithRag() {
        String answer = customerApp.doChatWithRag("我想查一下我的订单物流，顺便开一张发票", RUN_ID + "-rag");
        System.out.println("回答：" + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertTrue(answer.contains("物流") || answer.contains("发票"));
    }

    @Test
    void vectorStoreRetrieval() {
        List<Document> docs = customerAppVectorStore.similaritySearch(
                SearchRequest.builder().query("发票怎么开？").topK(3).build());
        System.out.println("检索到 " + docs.size() + " 条文档");
        assertFalse(docs.isEmpty());
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索：客服政策查询
        testMessage("帮我查一下最新的电商消费者权益保护法相关内容");

        // 测试网页抓取：查看官网常见问题
        testMessage("打开你们官网的帮助中心，看看退货流程是怎么写的？");

        // 测试资源下载：下载用户手册
        testMessage("帮我下载一份用户操作手册到本地文件");

        // 测试终端操作：执行数据汇总脚本
        testMessage("执行一个 Python 脚本，统计本周客服咨询量");

        // 测试文件操作：保存客服记录
        testMessage("把刚才的客服对话保存为档案文件");

        // 测试 PDF 生成
        testMessage("生成一份'本周客服工作总结'PDF，包含咨询分类、处理结果和后续跟进");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = customerApp.doChatWithTools(message, chatId);
        System.out.println("问题：" + message);
        System.out.println("回答：" + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void doChatWithMCP() {
        String chatId =UUID.randomUUID().toString();
//        测试地图mcp
        String answer = customerApp.doChatWithMCP("帮我看看江汉路附近有什么玩的地方", chatId);
        System.out.println("回答：" + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        //        测试图片搜索MCP
        answer = customerApp.doChatWithMCP("帮我搜索一张江汉路的图片", chatId);
        assertNotNull(answer);
    }

    /**
     * 验证 MCP 客户端连接：列出远程 MCP 服务器注册的全部工具，
     * 列表非空即说明高德地图 MCP 连接成功
     */
    @Test
    void listMcpTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        System.out.println("MCP 工具总数：" + callbacks.length);
        for (ToolCallback callback : callbacks) {
            System.out.println("MCP工具：" + callback.getToolDefinition().name()
                    + " —— " + callback.getToolDefinition().description());
        }
        assertFalse(callbacks.length == 0, "未注册任何 MCP 工具，MCP 客户端连接失败");
    }
}
