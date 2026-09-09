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
import java.util.function.Consumer;


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

    /**
     * 流式 token 出口：非空表示当前处于 SSE 流式模式（runStream）。
     * 子类（ToolCallAgent）在思考阶段应当边生成边往这里推，前端才有打字机效果；
     * 为 null 表示同步执行（run），保持一次拿全量结果。
     */
    protected transient Consumer<String> streamTokenSink;

    /**
     * 本 step 是否已通过 streamTokenSink 逐 token 推送过内容。
     * 用于避免 step() 返回整段文本后 runStream 又重复推一次。
     */
    protected transient boolean streamedThisStep = false;

    /**
     * 推送一个流式片段给前端（同步执行 run() 时为空操作）。
     */
    protected void pushToken(String token) {
        if (streamTokenSink != null && StrUtil.isNotBlank(token)) {
            streamedThisStep = true;
            streamTokenSink.accept(token);
        }
    }

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
        // 流式出口：模型每生成一个片段就立刻推给前端，而不是等整个 step 跑完
        // （sseEmitter.send 是同步 IO，这里只做异常兜底，不让它打断 agent 主循环）
        this.streamTokenSink = text -> {
            try {
                sseEmitter.send(buildEvent("answer", text));
            } catch (Exception sendEx) {
                log.warn("SSE 片段推送失败：{}", sendEx.getMessage());
            }
        };
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
                    streamedThisStep = false;
//            单步执行
                    String stepResult=step();
                    if (stepResult != null) {
                        results.add(stepResult);
                    }
                    String stepType=getLastStepType();
                    if ("answer".equals(stepType)) {
                        hasAnswer = true;
                    }
                    // 该 step 内容已逐 token 推给前端时，step() 返回 null，这里不再整段重复推。
                    // 整段推送（工具结果 / 兜底总结）时在末尾补换行，保证前端多段内容不会粘连成一坨
                    if (StrUtil.isNotBlank(stepResult)) {
                        sseEmitter.send(buildEvent(stepType,
                                "answer".equals(stepType) ? stepResult + "\n" : stepResult));
                    }
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
                        sseEmitter.send(buildEvent("answer", summary + "\n"));
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
                this.streamTokenSink = null;
                cleanup();
            }
        });
        // 处理超时
        sseEmitter.onTimeout(() -> {
            this.state=AgentState.ERROR;
            this.streamTokenSink = null;
            this.cleanup();
                log.warn("SSE connection timed out");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING){
                this.state=AgentState.FINISHED;
            }
            this.streamTokenSink = null;
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }
}
