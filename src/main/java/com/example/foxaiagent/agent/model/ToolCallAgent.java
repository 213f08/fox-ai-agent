package com.example.foxaiagent.agent.model;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ReAct 模式中负责"工具调用"的代理基类，可作为具备工具能力的各类 Agent 的父类。
 *
 * <p>继承自 {@link ReActAgent}。ReActAgent 的模板方法 {@link #step()} 会依次调用
 * {@link #think()}（判断是否需要行动）与 {@link #act()}（执行动作），由
 * {@link BaseAgent#run(String)} 驱动形成「思考 → 行动 → 观察」的循环，
 * 直到达到最大步数或 Agent 自行结束。</p>
 *
 * <p>与依赖 ChatModel 内置工具执行不同：本类通过 {@link ToolCallingManager} 自行管理
 * 工具的解析与调用，并将 {@link ChatOptions} 中的 internalToolExecutionEnabled 置为
 * {@code false}，从而关闭 Spring AI ChatModel "内部替你执行工具并自动追问"的流程，
 * 让 Agent 在 think/act 阶段自主控制每次工具调用的发起、结果观测与会话上下文维护。</p>
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent {

    /**
     * 该代理可使用的工具回调集合（例如：网页搜索、代码执行、MCP 服务等）。
     */
    private final ToolCallback[] availableTools;

    /**
     * 最近一次调用大模型后返回的响应，其中包含模型的工具调用请求或最终回复内容。
     */
    private ChatResponse toolCallChatResponse;

    /**
     * 工具调用管理器：负责把模型发起的工具调用请求解析出来，并分发到对应的 ToolCallback 执行。
     */
    private final ToolCallingManager toolCallingManager;

    /**
     * 每次请求大模型时携带的请求选项（模型、温度、工具执行策略等）。
     */
    private final ChatOptions chatOptions;

    /**
     * nextStepPrompt 是否已作为用户消息注入过上下文，保证每轮任务只注入一次，避免重复追加撑爆上下文。
     */
    private boolean nextStepPromptInjected;

    /**
     * 构造一个具备工具调用能力的 ReAct 代理。
     *
     * @param availableTools 该代理可用的工具列表，在思考阶段会随提示词一起提交给大模型
     * @param chatModel       底层大模型，用于继承其默认请求选项（模型名、multiModel 等配置）
     */
    public ToolCallAgent(ToolCallback[] availableTools, ChatModel chatModel) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = buildChatOptions(chatModel);
    }

    /**
     * 以 ChatModel 的默认选项为基底生成 Agent 请求选项，仅额外关闭内部自动执行工具。
     *
     * <p>注意不能「新建一个只含 internalToolExecutionEnabled 的 DashScopeChatOptions」：
     * 该对象的某些字段（如 {@code multiModel}）带有非空默认值 {@code false}，而 Spring AI 的
     * {@code ModelOptionsUtils.merge} 规则是 runtime 非空值覆盖 default，会把配置文件中
     * {@code multi-model: true} 覆盖成 false，导致 qwen3.8 等多模态模型被请求到普通文本接口，
     * 阿里云返回 HTTP 400「url error, please check url」。</p>
     *
     * @param chatModel 注入的 ChatModel（其默认选项含 model、multiModel 等配置）
     * @return 继承默认选项、仅关闭内部工具执行流程的请求选项
     */
    private static ChatOptions buildChatOptions(ChatModel chatModel) {
        ChatOptions defaults = chatModel.getDefaultOptions();
        if (defaults instanceof ToolCallingChatOptions toolCallingChatOptions) {
            // DashScopeChatModel.getDefaultOptions() 返回的是独立副本，改它不会污染全局 Bean 默认选项
            toolCallingChatOptions.setInternalToolExecutionEnabled(false);
            return defaults;
        }
        return ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 思考阶段：判断本轮是否需要调用工具、调用哪个工具。
     *
     * <p>约定返回 {@code true} 表示需要继续行动（例如模型发起了工具调用请求），
     * 返回 {@code false} 表示无需行动（如提示词为空、模型直接给出最终回答），
     * {@link ReActAgent#step()} 在返回 {@code false} 时不会再进入 {@link #act()}。</p>
     *
     * <p>典型流程：校验 {@code nextStepPrompt} 是否有内容 → 携带可用工具与历史消息调用
     * 大模型 → 将响应保存到 {@link #toolCallChatResponse } → 解析响应中是否存在工具调用请求。</p>
     *
     * @return {@code true} 表示需要执行 {@link #act()}，{@code false} 表示本轮思考结束
     */
    @Override
    public boolean think() {
        // 校验提示词：提示词为空则无需行动（有内容时后续在此发起工具调用）
        try {
            // nextStepPrompt 仅在首次思考时注入一次，避免每轮循环都重复追加同一段提示
            if (StrUtil.isNotBlank(getNextStepPrompt()) && !nextStepPromptInjected) {
                //校验提示词，接收用户提示词
                UserMessage userMessage = new UserMessage(getNextStepPrompt());
                getMessageList().add(userMessage);
                nextStepPromptInjected = true;
            }
            //调用AI大模型。获取工具调用结果
            List<Message> messageList = getMessageList();
            Prompt prompt=new Prompt(messageList,this.chatOptions);
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应
            this.toolCallChatResponse = chatResponse;
//        助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 工具调用列表
            List<AssistantMessage.ToolCall> toolCallList=assistantMessage.getToolCalls();
            if (toolCallList.isEmpty()){
                // 模型直接给出最终答复，无需再行动：把答复写回上下文并结束整个循环
                log.info("{} 思考完成：模型直接给出答复，本轮结束", getName());
                getMessageList().add(assistantMessage);
                setState(AgentState.FINISHED);
                return false;
            }
            // 若模型已给出文本回答、且本轮唯一动作是调用终止工具(doTerminate)，
            // 说明这是"最终回答 + 请求结束"：直接采纳文本回答，不再走工具执行路径，
            // 否则回答文本会被"工具执行结果"日志覆盖，用户看不到 AI 回复。
            boolean onlyTerminate = toolCallList.stream()
                    .allMatch(toolCall -> "doTerminate".equals(toolCall.name()));
            if (onlyTerminate && StrUtil.isNotBlank(assistantMessage.getText())) {
                log.info("{} 思考完成：模型给出最终回答并请求终止，直接采纳回答", getName());
                getMessageList().add(assistantMessage);
                setState(AgentState.FINISHED);
                return false;
            }
            // 一行日志概要展示本轮要调用的工具（含参数摘要，不刷屏）
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> toolCall.name() + "(" + shorten(toolCall.arguments(), 100) + ")")
                    .collect(Collectors.joining("；"));
            log.info("{} 思考完成：本轮将调用 {} 个工具 —— {}", getName(), toolCallList.size(), toolCallInfo);
            return true;
        } catch (Exception e) {
            log.error("{} 思考过程出错：{}", getName(), shorten(String.valueOf(e), 300));
            getMessageList().add(new AssistantMessage("思考过程出错：" + shorten(e.getMessage(), 200)));
            // 思考阶段失败时直接结束循环，避免同一错误重复刷满 maxSteps
            setState(AgentState.FINISHED);
            return false;
        }


    }

    /**
     * 行动阶段：执行思考阶段确定要做的动作，并返回行动结果。
     * @return 本轮行动的结果描述文本
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要被调用";
        }
        try {
            // 关键：执行 Prompt 的 options 必须是携带 toolCallbacks 的 ToolCallingChatOptions，
            // 否则 ToolCallingManager 无法解析到模型发起的工具调用（会抛 "No ToolCallback found"）
            ToolCallingChatOptions executeOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(availableTools)
                    .build();
            Prompt prompt = new Prompt(getMessageList(), executeOptions);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
            //记录消息上下文,conversationHistory已经包含了助手消息和工具调用结果
            setMessageList(toolExecutionResult.conversationHistory());
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
            //判断是否使用终止工具
            boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> response.name().equals("doTerminate"));
            if (terminateToolCalled){
                //任务结束，更改状态
                setState(AgentState.FINISHED
                );
            }
            String results = toolResponseMessage.getResponses().stream()
                    .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                    .collect(Collectors.joining("\n"));
            // 控制台只打工具名 + 返回长度 + 开头预览，完整结果仍保存在会话历史中供模型继续推理
            String resultSummary = toolResponseMessage.getResponses().stream()
                    .map(response -> {
                        String data = response.responseData();
                        return response.name() + " 返回 " + (data == null ? 0 : data.length()) + " 字符：" + shorten(data, 150);
                    })
                    .collect(Collectors.joining("；"));
            log.info("{} 工具执行结果：{}", getName(), resultSummary);
            return results;
        } catch (Exception e) {
            // 工具执行失败同样结束循环，避免下一轮思考继续发起同样的调用而空转
            log.error("{} 工具执行出错：{}", getName(), shorten(String.valueOf(e), 300));
            setState(AgentState.FINISHED);
            return "工具执行失败：" + shorten(e.getMessage(), 200);
        }

    }

    /**
     * 把文本压缩成适合打日志的形态：去掉换行/多余空白，超长截断。
     */
    private static String shorten(String text, int maxLen) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= maxLen) {
            return oneLine;
        }
        return oneLine.substring(0, maxLen) + "…(已截断,共" + oneLine.length() + "字符)";
    }
}
