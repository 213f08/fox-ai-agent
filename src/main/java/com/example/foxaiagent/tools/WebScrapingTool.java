package com.example.foxaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {
    @Tool(description = "用于从网页中抓取数据")
    public String scrapewebpage(@ToolParam(description = "网页的URL") String url) {
        // 这里可以添加实际的网页抓取逻辑
        try {
            Document document = Jsoup.connect(url).get();
            return document.html();
        } catch (IOException e) {
            return "抓取数据失败"+e.getMessage();
        }
    }
}
