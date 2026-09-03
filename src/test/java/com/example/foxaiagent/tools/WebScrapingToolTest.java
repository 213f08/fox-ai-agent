package com.example.foxaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebScrapingToolTest {

    @Test
    void scrapewebpage() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String url = "https://www.baidu.com/";
        String result = webScrapingTool.scrapewebpage(url);
        Assertions.assertNotNull(result);

    }
}