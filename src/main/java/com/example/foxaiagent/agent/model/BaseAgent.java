package com.example.foxaiagent.agent.model;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    protected void cleanup(){

    }
    public SseEmitter runStream(String userPrompt){
        SseEmitter sseEmitter = new SseEmitter(300000L);
        // 使用线程异步执行，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
//        基础校验
           try {
               if (state!=AgentState.IDLE){
                   sseEmitter.send("错误:无法从"+this.state+"状态运行代理");
                   sseEmitter.complete();
                   return;
               }
               if (StrUtil.isBlank(userPrompt)){
                   sseEmitter.send("不能使用空提示词运行代理");
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
                    sseEmitter.send(result);
                }
                if (currentStep>=maxSteps){
                    state=AgentState.FINISHED;
                    results.add("Terminated: Reached max steps ("+maxSteps+")");
                    sseEmitter.send("执行结束，达到最大步骤("+maxSteps+")");
                }
            } catch (Exception e) {
                state=AgentState.ERROR;
                log.info("Agent 执行出错：{}", e.getMessage());
                // 只打一行错误摘要，不打印完整堆栈，避免控制台刷屏
                try {
                    sseEmitter.send("错误: "+e.getMessage());
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
