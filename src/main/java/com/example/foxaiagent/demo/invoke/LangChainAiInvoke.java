package com.example.foxaiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * LangChain4j 方式调用。
 * 注意：langchain4j 1.19.0 起 ChatLanguageModel 已更名为 ChatModel（Streaming 同理），
 * 旧教程里的 ChatLanguageModel 对应新名字 ChatModel
 */
public class LangChainAiInvoke {

    public static void main(String[] args) {
        ChatModel qwenChatModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen3.8-flash")
                .build();

        String answer = qwenChatModel.chat("你好，我是麻瓜");
        System.out.println(answer);
    }
}
