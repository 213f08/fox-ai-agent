package com.example.foxaiagent.agent.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent{
    public abstract boolean think();
    public abstract String act();
    @Override
    public String step() {
      try {
          boolean shouldAct = think();
          if (!shouldAct) {
              return "思考完成,不需要行动";
          }
          return act();
      } catch (Exception e) {
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
}
