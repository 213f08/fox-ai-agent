package com.example.foxaiagent.agent.model;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Data
@Slf4j
public abstract class BaseAgent {
    //    核心属性
    private String name;
    //    提示词
    private String systemPrompt;
    private String nextStepPrompt;

    //    代理状态
    private AgentState state=AgentState.IDLE;

    //    执行步骤控制
    private int currentStep=0;
    private int maxSteps=10;

    //    LLM大模型
    private ChatClient chatClient;

    //    Mermory记忆(需要自主维护会话上下文)
    private List<Message> messageList=new ArrayList<>();

    public String run(String userPrompt){
//        基础校验
        if (state!=AgentState.IDLE){
            throw new RuntimeException("Agent is not idle"); // Agent 不为空闲状态
        }
        if (StrUtil.isBlank(userPrompt)){
            throw new RuntimeException("User prompt is empty");
        }

//        执行,更改状态
        this.state=AgentState.RUNNING;
//        记录消息上下文
        messageList.add(new UserMessage(userPrompt));
//        保存结果列表
        List<String> results=new ArrayList<>();
//      执行循环
        try {
            for (int i = 0; i <maxSteps && state!=AgentState.FINISHED ; i++) {
                int stepNumber=i+1;
                currentStep=stepNumber;
                log.info("Step {}/{} :", stepNumber,maxSteps);
//            单步执行
                String stepResult=step();
                String result="Step"+stepNumber+": "+stepResult;
                results.add(result);
            }
            if (currentStep>=maxSteps){
                state=AgentState.FINISHED;
                results.add("Terminated: Reached max steps ("+maxSteps+")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state=AgentState.ERROR;
            // 只打一行错误摘要，不打印完整堆栈，避免控制台刷屏
            String errorMsg = StrUtil.isBlank(e.getMessage())
                    ? e.getClass().getSimpleName()
                    : StrUtil.maxLength(e.getMessage(), 300);
            log.info("Agent 执行出错：{}", errorMsg);
            return "Error: "+errorMsg;
        }finally {
            cleanup();
        }
    }
    public abstract String step();

    /**
     * 最近一次 step() 的输出类型（tool=工具执行过程 / answer=最终回答）。
     * 由 ReActAgent 等子类覆盖，默认按最终回答处理。
     */
    public String getLastStepType() {
        return "answer";
    }

    /**
     * 构造结构化 SSE 事件：前端据此区分"工具过程"与"最终回答"，
     * 工具过程默认折叠，避免把工具返回的原始 JSON 全量铺给用户。
     */
    private static String buildEvent(String type, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return JSONUtil.toJsonStr(payload);
    }

    /**
     * 兜底生成最终回答：整轮只执行了工具、没有产出面向用户的回答时调用，
     * 让模型基于已有上下文（含工具返回结果）用自然语言直接回答用户。
     */
    protected String generateFinalAnswer() {
        try {
            if (chatClient == null || messageList.isEmpty()) {
                return null;
            }
            List<Message> promptMsgs = new ArrayList<>(messageList);
            promptMsgs.add(new UserMessage(
                    "请基于上面工具返回的结果，用简洁的自然语言直接回答用户最初的问题。" +
                    "要求：不要输出 JSON、不要罗列原始数据、不要提及工具名称，直接给出结论。"));
            return chatClient.prompt(new Prompt(promptMsgs)).call().content();
        } catch (Exception e) {
            log.warn("生成最终总结失败：{}", e.getMessage());
            return null;
        }
    }

    protected void cleanup(){

    }
    public SseEmitter runStream(String userPrompt){
        SseEmitter sseEmitter = new SseEmitter(300000L);
        // 使用线程异步执行，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
//        基础校验
           try {
               if (state!=AgentState.IDLE){
                   sseEmitter.send(buildEvent("answer", "错误:无法从"+this.state+"状态运行代理"));
                   sseEmitter.complete();
                   return;
               }
               if (StrUtil.isBlank(userPrompt)){
                   sseEmitter.send(buildEvent("answer", "不能使用空提示词运行代理"));
                   sseEmitter.complete();
                   return;
               }
           } catch (Exception e) {
                sseEmitter.completeWithError(e);
           }

//        执行,更改状态
            this.state=AgentState.RUNNING;
//        记录消息上下文
            messageList.add(new UserMessage(userPrompt));
//        保存结果列表
            List<String> results=new ArrayList<>();
            // 是否产出过面向用户的最终回答（用于兜底：只有工具过程时补一次总结）
            boolean hasAnswer = false;
//      执行循环
            try {
                for (int i = 0; i <maxSteps && state!=AgentState.FINISHED ; i++) {
                    int stepNumber=i+1;
                    currentStep=stepNumber;
                    log.info("Step {}/{} :", stepNumber,maxSteps);
//            单步执行
                    String stepResult=step();
                    results.add(stepResult);
                    String stepType=getLastStepType();
                    if ("answer".equals(stepType)) {
                        hasAnswer = true;
                    }
                    sseEmitter.send(buildEvent(stepType, stepResult));
                }
                if (currentStep>=maxSteps){
                    state=AgentState.FINISHED;
                    results.add("Terminated: Reached max steps ("+maxSteps+")");
                }
                // 兜底：整轮只跑了工具过程、模型没给出面向用户的回答时，
                // 让它基于工具结果补一次自然语言总结，避免用户只看到"已完成 N 个步骤"却没有答案
                if (!hasAnswer) {
                    String summary = generateFinalAnswer();
                    if (StrUtil.isNotBlank(summary)) {
                        sseEmitter.send(buildEvent("answer", summary));
                    }
                }
                // 正常结束：主动关闭 SSE 连接，否则前端 fetch 会一直等待直至超时
                sseEmitter.complete();
            } catch (Exception e) {
                state=AgentState.ERROR;
                log.info("Agent 执行出错：{}", e.getMessage());
                // 只打一行错误摘要，不打印完整堆栈，避免控制台刷屏
                try {
                    sseEmitter.send(buildEvent("tool", "错误: "+e.getMessage()));
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            }finally {
                cleanup();
            }
        });
        // 处理超时
        sseEmitter.onTimeout(() -> {
            this.state=AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING){
                this.state=AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }
}
