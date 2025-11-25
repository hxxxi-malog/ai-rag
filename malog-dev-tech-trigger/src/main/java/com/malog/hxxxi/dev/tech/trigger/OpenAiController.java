package com.malog.hxxxi.dev.tech.trigger;

import com.malog.hxxxi.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Auth : Malog
 * @Desc : OpenAi聊天模型控制
 * @Time : 2025/11/25 19:27
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatClient chatClient;
    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * 生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 聊天响应结果
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(String model, String message) {
        return chatClient.call(new Prompt(message, OpenAiChatOptions.builder().withModel(model).build()));
    }

    /**
     * 流式生成聊天响应
     *
     * @param model 模型名称
     * @param message 用户消息
     * @return 流式的聊天响应结果
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(String model, String message) {
        return chatClient.stream(new Prompt(message, OpenAiChatOptions.builder().withModel(model).build()));
    }

    /**
     * 生成基于RAG的流式聊天响应
     *
     * @param model 指定使用的AI模型名称
     * @param ragTag RAG标签，用于指定知识库中的特定文档集合
     * @param message 用户输入的消息内容
     * @return 返回流式的聊天响应结果
     */
    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStreamRag(@RequestParam("model") String model, @RequestParam("ragTag") String ragTag, @RequestParam("message") String message) {

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 构建文档搜索请求，根据用户消息在指定的知识标签下搜索最相关的5个文档
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // 使用指定模型和构建好的消息上下文，发起流式聊天请求并返回响应
        return chatClient.stream(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

}
