package com.example.foxaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 终端操作工具类（Demo 简化版）
 */
public class TerminalOperationTool {

    @Tool(description = "执行一条终端命令并返回输出")
    public String executeCommand(@ToolParam(description = "要执行的终端命令") String command) {
        StringBuilder output = new StringBuilder();
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            Process process = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", command).start()
                    : new ProcessBuilder("/bin/sh", "-c", command).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "执行命令失败：" + e.getMessage();
        }
    }
}
