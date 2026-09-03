package com.example.foxaiagent.demo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多查询拓展demo
 */
@Component
public class MultiQueryExpanderDemo {
    private ChatClient.Builder builder;
    public MultiQueryExpanderDemo (ChatModel dashscopeChatModel) {
        this.builder = ChatClient.builder(dashscopeChatModel);
    }
    // 创建查询重写转换器
   public List<Query> expandQuery(String query) {
       MultiQueryExpander queryExpander = MultiQueryExpander.builder()
               .chatClientBuilder(builder)
               .includeOriginal(false) // 不包含原始查询
               .numberOfQueries(3) // 生成3个查询变体
               .build();

// 执行查询扩展
// 将原始问题"请提供几种推荐的装修风格?"扩展成多个相关查询
       List<Query> queries = queryExpander.expand(
               new Query("请提供几种推荐的装修风格?"));
       return queries;
   }

}
