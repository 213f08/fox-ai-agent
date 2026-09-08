package com.example.foxaiagent.agent.model;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent{
    /**
     * 最近一次 step() 的输出类型：
     * - tool  ：工具执行过程（原始返回数据，前端折叠展示，默认不铺开给用户）
     * - answer：面向用户的最终回答
     */
    private String lastStepType = "answer";

    public abstract boolean think();
    public abstract String act();

    @Override
    public String step() {
      try {
          boolean shouldAct = think();
          if (!shouldAct) {
              lastStepType = "answer";   // 模型直接给出答复
              return lastAssistantText();
          }
          lastStepType = "tool";         // 本轮是工具执行过程
          return act();
      } catch (Exception e) {
          lastStepType = "tool";         // 异常摘要也归为过程信息
          // 只返回一行错误摘要，避免把整段堆栈/超长消息带进结果
          String msg = e.getMessage();
          String brief = (msg == null || msg.isBlank())
                  ? e.getClass().getSimpleName()
                  : msg.replaceAll("\\s+", " ").trim();
          if (brief.length() > 300) {
              brief = brief.substring(0, 300) + "…(已截断)";
          }
          return "步骤失败：" + brief;
      }
    }

    /**
     * 模型直接给出答复（无需调用工具）时，返回最后一条助手消息的文本内容，
     * 避免模型的真实回答被"思考完成"占位文案丢弃。
     */
    private String lastAssistantText() {
        List<Message> msgs = getMessageList();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Message m = msgs.get(i);
            if (m instanceof AssistantMessage am && StrUtil.isNotBlank(am.getText())) {
                return am.getText();
            }
        }
        return "思考完成,不需要行动";
    }
}
