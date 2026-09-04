package com.example.foximagesearchmcpserver.config;

import com.example.foximagesearchmcpserver.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 工具注册配置：把 @Tool 方法暴露为可被 MCP Client 调用的工具。
 */
@Configuration
public class McpToolConfiguration {

    @Bean
    public ImageSearchTool imageSearchTool(@Value("${pexels.api-key:}") String pexelsApiKey) {
        if (pexelsApiKey == null || pexelsApiKey.isBlank()) {
            throw new IllegalStateException("未配置 Pexels API Key：请设置环境变量 PEXELS_API_KEY 后再启动");
        }
        return new ImageSearchTool(pexelsApiKey);
    }

    @Bean
    public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}
