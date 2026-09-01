package com.example.foxaiagent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 云知识库（百炼）检索增强顾问配置
 * <p>
 * 对应课程 LoveAppRagCloudAdvisorConfig 的改写：
 * <ul>
 *   <li>new DashScopeApi(apiKey)  →  DashScopeApi.builder().apiKey(apiKey).build()
 *       （spring-ai-alibaba 1.1.2.0 移除了单参构造器，只保留 Builder）</li>
 *   <li>知识库名称抽成配置项 customer-app.knowledge-index，默认「麻瓜优选客服知识库」</li>
 * </ul>
 * 与内存向量库版（CustomerAppVectorStoreConfig）的区别：
 * 内存版 = 检索本地 SimpleVectorStore；本类 = 调用阿里云百炼云知识库检索 API。
 * 使用前提：先在百炼控制台创建同名知识库并上传文档（用名称，不是 ID）。
 */
@Configuration
@Slf4j
public class CustomerAppCloudAdvisorConfig {

    /**
     * DashScope API Key，取自 application-local.yml 的 spring.ai.dashscope.api-key
     */
    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    /**
     * 云知识库名称（必须与百炼控制台创建的知识库名称一致，注意用名称而不是 ID）
     */
    @Value("${customer-app.knowledge-index:客服助手知识库}")
    private String knowledgeIndex;

    /**
     * 创建"检索增强顾问" Bean：
     * 把基于云知识库的文档检索器（DashScopeDocumentRetriever）绑定到
     * RetrievalAugmentationAdvisor 上，之后每次对话都会先去云知识库检索相关文档，
     * 再把文档作为上下文拼给大模型，实现云端 RAG。
     */
    @Bean
    public Advisor customerAppRagCloudAdvisor() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();

        DocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(knowledgeIndex)
                        .build());

        log.info("已创建云知识库检索增强顾问，知识库索引：{}（请在百炼控制台使用相同名称创建知识库）", knowledgeIndex);

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();
    }
}
