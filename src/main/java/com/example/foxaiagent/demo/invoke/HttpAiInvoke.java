package com.example.foxaiagent.demo.invoke;

import java.util.Arrays;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/**
 * HTTP 方式调用：qwen3.8 系列是多模态模型，必须走 multimodal-generation（多模态）接口，
 * 且所有消息的 content 都要用 [{type,text}] 数组格式；走文本接口会报 url error
 */
public class HttpAiInvoke {

    public static void main(String[] args) {
        // 多模态接口地址（文本接口 /text-generation/generation 对 qwen3.8 无效）
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

        // API Key（测试阶段直接读取常量，正式项目建议改为 System.getenv("DASHSCOPE_API_KEY")）
        String apiKey = TestApiKey.API_KEY;

        // 多模态接口的 content 是数组格式：[{"type":"text","text":"..."}]
        JSONObject message1 = new JSONObject();
        message1.set("role", "system");
        message1.set("content", textPart("你是一个开发助手"));

        JSONObject message2 = new JSONObject();
        message2.set("role", "user");
        message2.set("content", textPart("你是谁"));

        JSONArray messages = new JSONArray();
        messages.add(message1);
        messages.add(message2);

        JSONObject input = new JSONObject();
        input.set("messages", messages);

        JSONObject parameters = new JSONObject();
        parameters.set("result_format", "message");

        JSONObject body = new JSONObject();
        body.set("model", "qwen3.8-flash");
        body.set("input", input);
        body.set("parameters", parameters);

        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .execute();

        System.out.println(response.body());
    }

    private static JSONArray textPart(String text) {
        JSONObject part = new JSONObject();
        part.set("type", "text");
        part.set("text", text);
        return new JSONArray(Arrays.asList(part));
    }
}
