package com.example.foxaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器
 */
@Component
@Slf4j
public class QueryRewriter {
    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder=ChatClient.builder(dashscopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }
    public String doQueryRewriter(String prompt) {
        Query query=new Query(prompt);
        Query transform = queryTransformer.transform(query);
        log.info("查询重写：{} → {}", prompt, transform.text());
        return transform.text();
    }
}
