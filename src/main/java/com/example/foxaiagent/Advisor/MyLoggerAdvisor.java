package com.example.foxaiagent.Advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor：在每轮对话前后打印 AI Request / AI Response。
 * <p>
 * 结构对照（教程 Spring AI 1.0 → 本项目 1.1.2）：
 * <ul>
 *   <li>CallAroundAdvisor / StreamAroundAdvisor → CallAdvisor + StreamAdvisor</li>
 *   <li>aroundCall / aroundStream              → adviseCall / adviseStream</li>
 *   <li>chain.nextAroundCall / nextAroundStream→ chain.nextCall / nextStream</li>
 *   <li>AdvisedRequest                         → ChatClientRequest（取用户文本：request.prompt().getUserMessage().getText()）</li>
 *   <li>AdvisedResponse                        → ChatClientResponse（取模型回复：response.chatResponse().getResult().getOutput().getText()）</li>
 *   <li>MessageAggregator                      → ChatClientMessageAggregator</li>
 * </ul>
 * 用 log.info（不是 debug），无需额外配日志级别就能直接看到。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * Advisor 名称
     *
     * @return 类名
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 执行优先级，数值越小越先执行
     *
     * @return order 值
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 调用前打印用户输入
     *
     * @param request 请求
     * @return 原样返回的请求
     */
    private ChatClientRequest before(ChatClientRequest request) {
        log.info("AI Request: {}", request.prompt().getUserMessage().getText());
        return request;
    }

    /**
     * 调用后打印模型回复
     *
     * @param response 响应
     */
    private void observeAfter(ChatClientResponse response) {
        log.info("AI Response: {}", response.chatResponse().getResult().getOutput().getText());
    }

    /**
     * 非流式调用拦截：调用前打请求日志，调用后打响应日志
     *
     * @param advisedRequest 请求
     * @param chain          Advisor 链
     * @return 模型响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest advisedRequest, CallAdvisorChain chain) {
        advisedRequest = before(advisedRequest);

        ChatClientResponse advisedResponse = chain.nextCall(advisedRequest);

        observeAfter(advisedResponse);

        return advisedResponse;
    }

    /**
     * 流式调用拦截：调用前打请求日志，聚合完整响应后再打响应日志
     *
     * @param advisedRequest 请求
     * @param chain          Advisor 链
     * @return 模型响应流
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest advisedRequest, StreamAdvisorChain chain) {
        advisedRequest = before(advisedRequest);

        Flux<ChatClientResponse> advisedResponses = chain.nextStream(advisedRequest);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(advisedResponses, this::observeAfter);
    }
}