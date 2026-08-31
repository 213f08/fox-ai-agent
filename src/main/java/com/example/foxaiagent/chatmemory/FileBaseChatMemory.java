package com.example.foxaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileBaseChatMemory implements ChatMemory {
    private final String BASE_DIR;
    /**
     * 单会话最多保留的消息条数，超出时裁掉最早的（滑动窗口）
     */
    private final int maxMessages;
    private static final Kryo kryo=new Kryo();
    static {
        kryo.setRegistrationRequired(false);
        //设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * 构造基于文件持久化的会话记忆
     *
     * @param dir          存储目录（自动创建）
     * @param maxMessages  单会话保留的最大消息条数
     */
    public FileBaseChatMemory(String dir, int maxMessages){
        this.BASE_DIR=dir;
        this.maxMessages=maxMessages;
        File basedir = new File(dir);
        if (!basedir.exists() && !basedir.mkdirs()) {
            // mkdirs 会连同不存在的父目录（如 tmp/）一起创建；失败说明路径不可写，直接快速失败
            throw new IllegalStateException("无法创建聊天记录存储目录: " + basedir.getAbsolutePath());
        }
    }
    @Override
    public void add(String conversationId, Message message) {
        saveConversation(conversationId, List.of(message));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        saveConversation(conversationId, messages);
    }

    /**
     * 读取指定会话的全部历史消息
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表；文件不存在时返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 保存会话消息（追加写：先读出已有历史，再把新消息合并后整体落盘）
     *
     * @param conversationId 会话 ID
     * @param messages       要追加的消息列表
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        // 读取已有历史，追加新消息（否则每次写入都会覆盖之前的记录）
        List<Message> allMessages = getOrCreateConversation(conversationId);
        allMessages.addAll(messages);
        // 滑动窗口：超出上限时裁掉最早的消息，防止文件无限增长
        if (allMessages.size() > maxMessages) {
            allMessages = new ArrayList<>(
                    allMessages.subList(allMessages.size() - maxMessages, allMessages.size()));
        }
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, allMessages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    每个会话文件单独存储
    private File getConversationFile(String conversationId){
        return new File(BASE_DIR,conversationId+".kryo");
    }

    /**
     * 获取或创建会话消息的列表
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表；如果文件不存在则返回空列表
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }
}
