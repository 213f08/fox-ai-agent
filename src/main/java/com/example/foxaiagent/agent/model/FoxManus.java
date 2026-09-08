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
                Decide whether tools are actually needed for the user's request:
                - For simple conversations, greetings, or general knowledge questions, answer directly in natural language WITHOUT calling any tool.
                - Only call tools when the task truly requires them (web search, scraping, PDF generation, image search, etc.).
                - When you do use tools, examine the results and continue until the task is complete.
                - Do NOT call the same kind of tool more than twice, and never search repeatedly for more of the same thing.
                - IMPORTANT: as soon as you have enough information, stop calling tools and write your final answer in natural language, then call `terminate`. Never call `terminate` without giving the answer first.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 步数上限：过大会让模型反复调用同类工具（如连续多次搜索），
        // 单次对话耗时几分钟、用户体验差；8 步足够覆盖"搜索→抓取→生成"类任务。
        setMaxSteps(8);
//        初始化AI对话客户端
        ChatClient chatClient=ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
