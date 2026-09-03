package com.example.foxaiagent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Re2（Re-Reading）策略 Advisor：把用户问题复读一遍再发给模型
 * "Read the question again" 可提升模型对问题的理解准确率
 * <p>
 * 对应教程旧版 API 的改写（Spring AI 1.1.2）：
 * <ul>
 *   <li>implements CallAroundAdvisor / StreamAroundAdvisor → CallAdvisor / StreamAdvisor</li>
 *   <li>aroundCall(AdvisedRequest, chain) → adviseCall(ChatClientRequest, chain)</li>
 *   <li>chain.nextAroundCall(...) → chain.nextCall(...)</li>
 *   <li>改 userText + userParams → 重建消息列表里的最后一条 UserMessage</li>
 *   <li>getName() 覆写 → toString() 覆写（新版 Advisor 接口已无 getName）</li>
 * </ul>
 * 注：教程用 {re2_input_query} 占位符 + userParams 传参，新版里模板渲染发生在
 * Advisor 链之前，占位符不会再被渲染，故直接用字符串拼接，效果等价。
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 改写请求：将最后一条用户消息替换为「原问题 + 复读一遍」的 Re2 格式
     *
     * @param request 原始请求
     * @return 改写后的请求
     */
    private ChatClientRequest before(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();

        // Re2：原问题 + 复读一遍
        String re2Text = userText + "\nRead the question again: " + userText;

        // 复制消息列表，替换最后一条（当前轮的 UserMessage），保留 system 和历史消息
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        int lastIndex = messages.size() - 1;
        if (lastIndex >= 0 && messages.get(lastIndex) instanceof UserMessage) {
            messages.set(lastIndex, new UserMessage(re2Text));
        }

        Prompt newPrompt = new Prompt(messages, request.prompt().getOptions());
        return request.mutate().prompt(newPrompt).build();
    }

    /**
     * 非流式调用拦截：先复读改写请求，再交给链上下一个 Advisor 执行
     *
     * @param request 请求
     * @param chain   Advisor 链
     * @return 模型响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(this.before(request));
    }

    /**
     * 流式调用拦截：先复读改写请求，再交给链上下一个 Advisor 执行
     *
     * @param request 请求
     * @param chain   Advisor 链
     * @return 模型响应流
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(this.before(request));
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
     * Advisor 名称
     *
     * @return 类名
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
