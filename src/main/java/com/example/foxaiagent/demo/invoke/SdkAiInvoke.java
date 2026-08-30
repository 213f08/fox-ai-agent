package com.example.foxaiagent.demo.invoke;

import java.util.Arrays;
import java.util.Collections;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.JsonUtils;

/**
 * SDK 方式调用：qwen3.8 系列是多模态模型，必须使用 MultiModalConversation（多模态接口），
 * 使用 Generation（文本接口）会报 url error
 */
public class SdkAiInvoke {

    public static void main(String[] args) {
        try {
            MultiModalConversation conv = new MultiModalConversation();

            MultiModalMessage systemMsg = MultiModalMessage.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(Arrays.asList(Collections.singletonMap("text", "You are a helpful assistant.")))
                    .build();

            MultiModalMessage userMsg = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(Collections.singletonMap("text", "你是谁？")))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(TestApiKey.API_KEY)
                    .model("qwen3.8-flash")
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .build();

            MultiModalConversationResult result = conv.call(param);
            System.out.println(JsonUtils.toJson(result));
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            System.err.println("An error occurred while calling the generation service: " + e.getMessage());
        }
        System.exit(0);
    }
}
