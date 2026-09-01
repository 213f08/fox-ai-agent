package com.example.foxaiagent.rag;

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

    @Bean
    public VectorStore customerAppVectorStore(EmbeddingModel embeddingModel, CustomerAppDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        List<Document> documents = documentLoader.loadMarkdowns();
        log.info("加载到 {} 份知识文档，开始写入向量库...", documents.size());
        for (Document doc : documents) {
            String text = doc.getText().replace("\n", " ").trim();
            String preview = text.length() > 60 ? text.substring(0, 60) + "..." : text;
            log.info("  - [{}] {}", doc.getMetadata().get("filename"), preview);
        }
        vectorStore.add(documents);
        log.info("向量库初始化完成，共写入 {} 个文档片段", documents.size());
        return vectorStore;
    }
}
