package com.example.foxaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 自定义的RAG检索增强顾问的工厂
 */
public class CustomerAppRagCustomerAdvisorFactory {
    public static Advisor creatCustomerAppRagCustomerAdvisor (VectorStore vectorStore,String status) {
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) //过滤条件
                .similarityThreshold(0.5)   //相似度阈值
                .topK(3) //返回的文档数量
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(CustomerAppContextualQueryAugmenterFactory.createInstance())
//                查询增强顾问
                .build();
    }
}
