package com.example.foxaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 .kryo 会话记忆文件查看器
 * <p>
 * Kryo 是二进制序列化格式，编辑器直接打开是乱码，
 * 必须用与写入端相同的 Kryo 配置反序列化后才能读。
 * <p>
 * 使用方式（二选一）：
 * <ul>
 *   <li>IDEA 里右键本类 → Run 'KryoMemoryViewer.main()'（默认读 tmp/chat-memory）</li>
 *   <li>带参数运行：args[0] 传目录路径，如 target/test-tmp/xxx</li>
 * </ul>
 */
public class KryoMemoryViewer {

    private static final Kryo kryo = new Kryo();

    static {
        // 必须与 FileBaseChatMemory 的配置完全一致，否则反序列化会失败
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public static void main(String[] args) {
        String dir = args.length > 0 ? args[0]
                : System.getProperty("user.dir") + "/tmp/chat-memory";
        dump(dir);
    }

    /**
     * 打印指定目录下所有会话文件的内容
     *
     * @param dir 存放 .kryo 文件的目录
     */
    public static void dump(String dir) {
        File baseDir = new File(dir);
        File[] files = baseDir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (files == null || files.length == 0) {
            System.out.println("目录 " + dir + " 下没有 .kryo 文件");
            return;
        }
        for (File file : files) {
            String conversationId = file.getName().replace(".kryo", "");
            System.out.println("==========================================");
            System.out.println("会话 ID: " + conversationId
                    + "  (" + file.length() + " bytes)");
            System.out.println("==========================================");
            List<Message> messages = read(file);
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                System.out.printf("[%d] %-10s | %s%n", i, msg.getMessageType(), msg.getText());
            }
        }
    }

    /**
     * 反序列化单个会话文件
     *
     * @param file .kryo 文件
     * @return 该会话的消息列表；读取失败返回空列表
     */
    @SuppressWarnings("unchecked")
    private static List<Message> read(File file) {
        try (Input input = new Input(new FileInputStream(file))) {
            return (List<Message>) kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            System.out.println("读取失败: " + file.getName() + " -> " + e.getMessage());
            return List.of();
        }
    }
}