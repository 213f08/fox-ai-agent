package com.example.foximagesearchmcpserver.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Pexels 图片搜索工具（demo）。
 * <p>
 * 接口：GET https://api.pexels.com/v1/search
 * 鉴权：请求头 Authorization 直接放 API Key（不带 Bearer）
 */
public class ImageSearchTool {

    private static final String SEARCH_URL = "https://api.pexels.com/v1/search";

    private final String apiKey;

    public ImageSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }


    @Tool(description = "按关键词在 Pexels 搜索图片，返回前 5 张图片的标题和直链")
    public String searchImages(String query) {
        try {
            String body = HttpRequest.get(SEARCH_URL)
                    .header("Authorization", apiKey)
                    .form("query", query)
                    .timeout(10_000)
                    .execute()
                    .body();
            JSONArray photos = JSONUtil.parseObj(body).getJSONArray("photos");
            if (photos == null || photos.isEmpty()) {
                return "未找到相关图片";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(photos.size(), 5); i++) {
                JSONObject photo = photos.getJSONObject(i);
                String alt = photo.getStr("alt", query);
                String src = photo.getJSONObject("src").getStr("original");
                sb.append(i + 1).append(". ").append(alt).append(" - ").append(src).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "图片搜索失败：" + e.getMessage();
        }
    }
}
