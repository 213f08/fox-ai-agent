package com.example.foxaiagent.agent.model;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;


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
}
