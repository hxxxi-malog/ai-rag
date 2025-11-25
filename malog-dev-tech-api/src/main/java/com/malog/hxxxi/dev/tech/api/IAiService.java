package com.malog.hxxxi.dev.tech.api;

import org.springframework.ai.chat.ChatResponse;
import reactor.core.publisher.Flux;

public interface IAiService {

    /**
     * 生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 聊天响应结果
     */
    ChatResponse generate(String model, String message);

    /**
     * 流式生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 流式的聊天响应结果
     */
    Flux<ChatResponse> generateStream(String model, String message);

    /**
     * 带rag的流式生成聊天响应
     *
     * @param model 模型名称
     * @param ragTag rag标签
     * @param message 用户消息
     * @return 流式的聊天响应结果
     */
    Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message);

}
