package com.example.foxaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// 教程学完后已停用：@Component 会让每次启动（含所有测试）都自动调一次 API、多花约 6 秒
// 想单独验证时，取消下行注释再运行即可
//@Component
public class SpringAiAiInvoke implements CommandLineRunner {
    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
       AssistantMessage assistantMessage= dashscopeChatModel.call(new Prompt("你好,我是麻瓜"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}
