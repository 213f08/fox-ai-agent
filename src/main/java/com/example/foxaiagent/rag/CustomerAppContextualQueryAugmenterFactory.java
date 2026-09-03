package com.example.foxaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 自定义上下文查询增强器工厂
 */
public class CustomerAppContextualQueryAugmenterFactory {
    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyPromptTemplate = new PromptTemplate("" +
                "你应该输出下面的内容: 抱歉，我只能回答订单相关问题，别的没有办法帮到您，又问题可以联系人工客服");
        return ContextualQueryAugmenter.builder()
//                找不到时输出的内容
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyPromptTemplate)
                .build();
    }
}
