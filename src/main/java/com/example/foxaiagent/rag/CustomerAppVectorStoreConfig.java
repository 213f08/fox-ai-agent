package com.example.foxaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 向量库配置：
 *
 *   使用内存版 SimpleVectorStore（由 DashScope EmbeddingModel 做向量化）</li>
 *   启动时优先从本地缓存文件加载（跳过重复向量化，秒启）</li>
 *   缓存不存在时从 classpath 兜底（src/main/resources/vector-store.json，随部署包分发）</li>
 *   两者都无才首次向量化 document/*.md 并落盘缓存</li>
 *
 * <p>local 与 prod 都启用：
 * 生产部署包内置了预构建缓存（vector-store.json），云端启动直接 load，秒级完成，
 * 不会触发"逐片段调 embedding/关键词增强"的慢路径（那会拖垮容器存活探针）。
 * 文档内容更新后：本地删 tmp/vector-store.json 重启重建缓存，
 * 再覆盖 src/main/resources/vector-store.json 并提交，新部署包即携带新知识。</p>
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

    /**
     * classpath 兜底缓存：随 jar/镜像分发的预构建向量库，
     * 保证云端新容器"零 API 调用、零长启动"即可用上 RAG。
     */
    private static final String CLASSPATH_CACHE = "vector-store.json";

    @Bean
    public VectorStore customerAppVectorStore(EmbeddingModel embeddingModel, CustomerAppDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // ===== ① 本地缓存不存在时，尝试从 classpath 兜底缓存恢复 =====
        if (!CACHE_FILE.exists()) {
            prepareClasspathCache();
        }
        // ===== ② 有缓存：直接加载，跳过全部 embedding API 调用 =====
        if (CACHE_FILE.exists()) {
            try {
                vectorStore.load(CACHE_FILE);
                long kb = CACHE_FILE.length() / 1024;
                int chunks = countCacheChunks();
                log.info("[RAG] 从缓存加载向量库：{}（跳过向量化，{} 个片段，缓存 {} KB）",
                        CACHE_FILE.getPath(), chunks >= 0 ? chunks : "未知", kb);
                return vectorStore;
            } catch (Exception e) {
                log.warn("[RAG] 缓存加载失败（{}），回退为重新向量化", e.getMessage());
            }
        }

        // ===== ③ 无任何缓存（首次启动）：加载文档 → 增强 → 入库 → 落盘 =====
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
     * 把 classpath 内的预构建缓存（vector-store.json）复制到 tmp，
     * 让云端新容器无需向量化即可秒载知识库。失败仅告警，不阻断启动。
     */
    private void prepareClasspathCache() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_CACHE);
            if (!resource.exists()) {
                log.info("[RAG] classpath 无兜底缓存（{}），本地也没有 → 将执行首次向量化", CLASSPATH_CACHE);
                return;
            }
            File tmpDir = CACHE_FILE.getParentFile();
            if (tmpDir != null && !tmpDir.exists() && !tmpDir.mkdirs()) {
                log.warn("[RAG] 缓存目录创建失败：{}", tmpDir.getAbsolutePath());
                return;
            }
            Files.copy(resource.getInputStream(), CACHE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("[RAG] 已从 classpath 恢复预构建缓存到 {}（{} KB）",
                    CACHE_FILE.getPath(), CACHE_FILE.length() / 1024);
        } catch (Exception e) {
            log.warn("[RAG] 从 classpath 恢复缓存失败：{}", e.getMessage());
        }
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

