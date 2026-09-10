package com.example.foxaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器：把口语化问题改写成更适合向量检索的简洁表述。
 * <p>
 * 覆盖了 Spring AI 内置的英文默认模板，原因有两个：
 * <ol>
 *   <li>默认模板是 "Given a user query, rewrite it ..." 的英文指令，模型会跟着用英文作答。
 *       中文闲聊词会被翻译成英文（实测「哦豁」→「Unexpected issue」），
 *       拿英文 query 去检索中文知识库，相似度分数虚高、语义也对不上。</li>
 *   <li>默认模板要求「改写得更具体」，闲聊/感叹词会被它硬生生补全成一个正经问题，
 *       于是任何输入都会检索到文档，前端误显示「调用了知识库」。</li>
 * </ol>
 * 自定义模板强制：同语种、只改写不翻译、闲聊原样返回。
 */
@Component
@Slf4j
public class QueryRewriter {

    /**
     * 占位符 {target} 与 {query} 是框架硬性要求（PromptAssert.templateHasRequiredPlaceholders），
     * 自定义模板必须保留，否则构造 RewriteQueryTransformer 时会抛异常。
     */
    private static final PromptTemplate ZH_PROMPT_TEMPLATE = new PromptTemplate("""
            你是中文饮食健康知识库的检索查询改写器，把用户问题改写成更适合向量检索的简洁表述。
            严格遵守以下规则：
            1. 输出语种必须与原始问题一致：原问题是中文就输出中文，禁止翻译成英文。
            2. 只做同义改写和精简，不要新增信息、不要回答这个问题、不要任何解释。
            3. 如果原始问题是闲聊、感叹词、打招呼或没有明确语义（例如"哦豁""哈哈""你好""？？？"），
               就原样输出它，不要补全成任何正式问题。
            4. 只输出一行改写后的查询文本，不要前缀、引号、标点说明或换行。

            目标检索系统：{target}
            原始问题：{query}
            改写结果：
            """);

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .promptTemplate(ZH_PROMPT_TEMPLATE)
                // 目标系统描述也改成中文，避免模型被英文词带跑
                .targetSearchSystem("中文向量知识库")
                .build();
    }
    public String doQueryRewriter(String prompt) {
        Query query=new Query(prompt);
        Query transform = queryTransformer.transform(query);
        log.info("查询重写：{} → {}", prompt, transform.text());
        return transform.text();
    }
}
