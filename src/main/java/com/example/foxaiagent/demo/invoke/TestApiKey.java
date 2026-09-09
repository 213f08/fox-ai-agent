package com.example.foxaiagent.demo.invoke;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.setting.yaml.YamlUtil;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 本地测试用的 API Key 提供者（已被 .gitignore 忽略，禁止提交到仓库）
 * <p>
 * 原项目里该类只写一个硬编码常量，clone 到新机器后文件丢失会导致
 * demo/invoke 下几个示例类编译失败。这里改成"按优先级取值"，避免再次丢失：
 * <ol>
 *   <li>环境变量 DASHSCOPE_API_KEY（推荐，最安全）</li>
 *   <li>classpath:application-local.yml 里的 spring.ai.dashscope.api-key（本机已配置）</li>
 * </ol>
 * 取不到时返回空串，示例运行时会报鉴权错误而不是编译错误。
 */
public final class TestApiKey {

    public static final String API_KEY = resolveApiKey();

    private TestApiKey() {
    }

    private static String resolveApiKey() {
        String env = System.getenv("DASHSCOPE_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        try {
            Object yaml = YamlUtil.load(
                    new InputStreamReader(
                            new ClassPathResource("application-local.yml").getStream(),
                            StandardCharsets.UTF_8));
            Object value = dig(yaml, "spring", "ai", "dashscope", "api-key");
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        } catch (Exception e) {
            System.err.println("[TestApiKey] 读取 application-local.yml 失败：" + e.getMessage());
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Object node, String... keys) {
        Object current = node;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }
}
