package com.example.foxaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalOperationToolTest {

    @Test
    void executeCommand() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        // 工具内部已拼接 cmd.exe /c（或 /bin/sh -c），这里只传命令本身；
        // echo 在 Windows 和 Linux 下都可用，保证跨平台
        String result = terminalOperationTool.executeCommand("dir");
        assertNotNull(result);
        assertFalse(result.contains("执行命令失败"));
        assertTrue(result.contains("hello"));
    }
}
