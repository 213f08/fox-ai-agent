package com.example.foxaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义基于token切词器
 */
@Component
class MyTokenTextSplitter {

    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        return splitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(1000)
            .withMinChunkSizeChars(400)
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(5000)
            .withKeepSeparator(true)
            .build();
        return splitter.apply(documents);
    }
}