package com.example.foxaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 向量库配置：
 * <ul>
 *   <li>使用内存版 SimpleVectorStore（由 DashScope EmbeddingModel 做向量化）</li>
 *   <li>启动时自动加载 classpath:document/*.md 并写入向量库，供 RAG 检索使用</li>
 * </ul>
 */
@Configuration
@Slf4j
public class CustomerAppVectorStoreConfig {
    @Resource
    private  MyTokenTextSplitter myTokenTextSplitter;
    @Resource
    private MyKeyWordEnricher myKeyWordEnricher;
    @Bean
    public VectorStore customerAppVectorStore(EmbeddingModel embeddingModel, CustomerAppDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        List<Document> documents = documentLoader.loadMarkdowns();
        log.info("加载到 {} 份知识文档，开始写入向量库...", documents.size());
        //效果不好，所以注释，仅学习
//        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);
//        自动补充关键词元信息
        List<Document> enrichDocuments = myKeyWordEnricher.enrichDocuments(documents);
        vectorStore.add(enrichDocuments);
        log.info("向量库初始化完成，共写入 {} 个文档片段", enrichDocuments.size());
        return vectorStore;
    }
}
