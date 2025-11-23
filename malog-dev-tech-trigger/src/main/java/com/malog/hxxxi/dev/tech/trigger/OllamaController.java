package com.malog.hxxxi.dev.tech.trigger;

import com.malog.hxxxi.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * @Auth : Malog
 * @Desc : 聊天模型控制
 * @Time : 2025/11/23 17:06
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient chatClient;

    /**
     * 生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 聊天响应结果
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return chatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * 流式生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 流式的聊天响应结果
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return chatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

}
