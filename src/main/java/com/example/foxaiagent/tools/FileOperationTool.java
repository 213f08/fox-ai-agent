package com.example.foxaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.example.foxaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具类
 */
public class FileOperationTool {
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR+"/file";
    @Tool(description = "Read file content from a given file path")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        // TODO: 读取文件内容
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath); // 读取文件内容
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
    @Tool(description = "Write content to a file at a given file path")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName, @ToolParam(description = "Content to write to the file") String content) {
        // TODO: 写入文件内容
        String filePath = FILE_DIR + "/" + fileName;
        try {
            FileUtil.writeUtf8String(content, filePath); // 写入文件内容
            return "File written successfully";
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}
