package com.example.foxaiagent.demo.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class MultiQueryExpanderDemoTest {
    @Resource
    private MultiQueryExpanderDemo multiQueryExpanderDemo;
    @Test
    void expandQuery() {
        List<Query> expandQuery = multiQueryExpanderDemo.expandQuery("请提供几种推荐的装修风格?");
        Assertions.assertNotNull(expandQuery);
    }
}