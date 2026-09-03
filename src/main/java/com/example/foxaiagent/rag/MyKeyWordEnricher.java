package com.example.foxaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于AI的文档元信息增强器(为文档增强)
 */
@Component
public class MyKeyWordEnricher {
    @Resource
    private ChatModel dashscopeChatModel;
    public List<Document> enrichDocuments(List<Document> document) {
        KeywordMetadataEnricher keywordMetadataEnricher=new KeywordMetadataEnricher(dashscopeChatModel,5);
        return keywordMetadataEnricher.apply(document);
    }
}
