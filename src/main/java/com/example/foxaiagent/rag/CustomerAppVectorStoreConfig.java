package com.example.foxaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;

/**
 * 向量库配置：
 *
 *   使用内存版 SimpleVectorStore（由 DashScope EmbeddingModel 做向量化）</li>
 *   启动时优先从本地缓存文件加载（跳过重复向量化，秒启）</li>
 *   缓存不存在时自动加载 classpath:document/*.md 入库，并落盘缓存</li>
 *   文档更新后需删除缓存文件 tmp/vector-store.json 才会重新向量化</li>
 *
 */
@Configuration
@Slf4j
public class CustomerAppVectorStoreConfig {
    @Resource
    private  MyTokenTextSplitter myTokenTextSplitter;
    @Resource
    private MyKeyWordEnricher myKeyWordEnricher;

    /** 向量库本地缓存文件（SimpleVectorStore 序列化为 JSON） */
    private static final File CACHE_FILE =
            new File(System.getProperty("user.dir") + "/tmp", "vector-store.json");

    @Bean
    public VectorStore customerAppVectorStore(EmbeddingModel embeddingModel, CustomerAppDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // ===== ① 有缓存：直接加载，跳过全部 embedding API 调用 =====
        if (CACHE_FILE.exists()) {
            try {
                vectorStore.load(CACHE_FILE);
                long kb = CACHE_FILE.length() / 1024;
                int chunks = countCacheChunks();
                log.info("[RAG] 从本地缓存加载向量库：{}（跳过向量化，{} 个片段，缓存 {} KB）",
                        CACHE_FILE.getPath(), chunks >= 0 ? chunks : "未知", kb);
                return vectorStore;
            } catch (Exception e) {
                log.warn("[RAG] 缓存加载失败（{}），回退为重新向量化", e.getMessage());
            }
        }

        // ===== ② 无缓存（首次启动）：加载文档 → 增强 → 入库 → 落盘 =====
        List<Document> documents = documentLoader.loadMarkdowns();
        log.info("[RAG] 首次启动：加载到 {} 份知识文档，开始写入向量库...", documents.size());
        //效果不好，所以注释，仅学习
//        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);
        // 自动补充关键词元信息。
        // 注意：这里每份文档都会调一次 ChatModel（很耗免费额度），若接口不可用/额度不足
        // 不应拖垮整个应用或测试的启动，降级为原文写入即可。
        List<Document> enrichDocuments;
        try {
            enrichDocuments = myKeyWordEnricher.enrichDocuments(documents);
        } catch (Exception e) {
            log.warn("[RAG] 关键词增强失败（AI 接口不可用或免费额度不足），已降级为原文写入：{}", e.getMessage());
            enrichDocuments = documents;
        }
        vectorStore.add(enrichDocuments);
        log.info("[RAG] 向量化完成，共写入 {} 个文档片段", enrichDocuments.size());

        // 落盘缓存，下次启动直接 load，不再调 embedding
        try {
            File tmpDir = CACHE_FILE.getParentFile();
            if (tmpDir != null && !tmpDir.exists() && !tmpDir.mkdirs()) {
                log.warn("[RAG] 缓存目录创建失败：{}", tmpDir.getAbsolutePath());
            } else {
                vectorStore.save(CACHE_FILE);
                log.info("[RAG] 向量库已缓存到：{}（下次启动跳过向量化）", CACHE_FILE.getPath());
            }
        } catch (Exception e) {
            log.warn("[RAG] 向量库缓存落盘失败（不影响本次使用）：{}", e.getMessage());
        }
        return vectorStore;
    }

    /**
     * 统计缓存文件里的文档片段数（顶层 JSON 每个 key = 一个片段）
     * 拿不到时返回 -1，日志里显示兜底文案
     */
    private int countCacheChunks() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(CACHE_FILE).size();
        } catch (Exception e) {
            log.warn("[RAG] 解析缓存统计片段数失败：{}", e.getMessage());
            return -1;
        }
    }
}
