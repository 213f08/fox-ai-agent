package com.example.foxaiagent.controller;

import com.example.foxaiagent.agent.model.FoxManus;
import com.example.foxaiagent.app.CustomerApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

@RestController
@RequestMapping("/ai")
public class AiController {
    @Resource
    private CustomerApp customerApp;
    @Resource
    private ToolCallback[] allTools;
    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 基础多轮对话（带记忆）
     * GET /ai/customer_app/chat/sync?message=你好&chatId=test1
     */
    @GetMapping("/customer_app/chat/sync")
    public String doChat(@RequestParam String message, @RequestParam String chatId) {
        return customerApp.doChat(message, chatId);
    }

    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(@RequestParam String message, @RequestParam String chatId) {
        FoxManus foxManus = new FoxManus(allTools, dashscopeChatModel);
        return foxManus.runStream(message);
    }

    /**
     * SSE 流式调用：逐 token 推送，纯字符串流
     * GET /ai/customer_app/chat/sse?message=你好&chatId=test6
     */
    @GetMapping(value = "/customer_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithCustomerAppSSE(@RequestParam String message, @RequestParam String chatId) {
        return customerApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用：包装成标准 ServerSentEvent，前端可直接用 EventSource 接收
     * GET /ai/customer_app/chat/server_sent_event?message=你好&chatId=test7
     */
    @GetMapping(value = "/customer_app/chat/server_sent_event", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doChatWithCustomerAppServerSentEvent(@RequestParam String message, @RequestParam String chatId) {
        return customerApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 饮食健康助手「小养」流式问答（带本地 RAG 检索增强；无本地向量库时自动退化为纯对话）
     * GET /ai/diet/chat/sse?message=减脂期晚上能吃主食吗&chatId=test
     */
    @GetMapping(value = "/diet/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doDietChatSSE(@RequestParam String message, @RequestParam String chatId) {
        return customerApp.doDietRagChatStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}
