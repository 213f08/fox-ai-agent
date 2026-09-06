package com.example.foxaiagent.agent.model;
import com.example.foxaiagent.advisor.MyLoggerAdvisor;
import com.example.foxaiagent.agent.model.ToolCallAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public  class FoxManus extends ToolCallAgent{

    /**
     * 构造一个具备工具调用能力的 ReAct 代理。
     *
     * @param allTools 该代理可用的工具列表，在思考阶段会随提示词一起提交给大模型
     * @param dashscopeChatModel 底层 DashScope 大模型（默认选项含 model、multiModel 等配置）
     */
    public FoxManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools, dashscopeChatModel);
        this.setName("FoxManus");
        String SYSTEM_PROMPT = """
                You are FoxManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        setMaxSteps(20);
//        初始化AI对话客户端
        ChatClient chatClient=ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
