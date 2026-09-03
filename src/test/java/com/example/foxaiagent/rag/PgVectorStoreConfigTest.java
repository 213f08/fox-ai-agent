package com.example.foxaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PgVectorStoreConfigTest {

    @Autowired
    VectorStore vectorStore;

    @Test
    void vectorStore() {
        List<Document> documents = List.of(
                new Document("麻瓜优选支持七天无理由退货，自物流签收次日零点起算。", Map.of("meta1", "退货退款政策")),
                new Document("超级会员月卡二十五元，年卡一百九十九元，全场自营商品享九五折优惠。", Map.of("meta2", "会员制度")),
                new Document("订单发货后物流单号将发送至下单手机号短信，签收后四十八小时内显示运输中属正常延迟。", Map.of("meta3", "物流配送")));

        // Add the documents to PGVector
        vectorStore.add(documents);

        // Retrieve documents similar to a query
        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder().query("退货政策").topK(5).build());

        // 校验召回结果
        assertNotNull(results, "相似检索不应返回 null");
        assertFalse(results.isEmpty(), "应至少召回一条与『退货政策』相关的文档");
        System.out.println("相似检索召回 " + results.size() + " 条文档：");
        results.forEach(d -> System.out.println("  - " + d.getText()));
    }
}
