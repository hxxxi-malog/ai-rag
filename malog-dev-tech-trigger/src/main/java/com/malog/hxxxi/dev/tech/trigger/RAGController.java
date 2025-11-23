package com.malog.hxxxi.dev.tech.trigger;

import com.malog.hxxxi.dev.tech.api.IRAGService;
import com.malog.hxxxi.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Auth : Malog
 * @Desc :
 * @Time : 2025/11/23 20:35
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 查询RAG标签列表
     *
     * @return Response<List<String>> 包含标签列表的响应对象
     *         - code: 响应码，"0000"表示成功
     *         - info: 响应信息，"上传成功"表示操作结果
     *         - data: 标签列表数据
     */
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        // 从Redis中获取ragTag列表
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("上传成功")
                .data(elements)
                .build();
    }


    /**
     * 上传文件并处理文档
     *
     * @param ragTag 标签标识，用于标识知识库分类
     * @param files 待上传的文件列表
     * @return 响应结果，包含上传状态信息
     */
    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam List<MultipartFile> files) {
        log.info("upload files start: {}", ragTag);
        for (MultipartFile file : files) {
            // 使用Tika解析器读取文件内容并转换为文档对象
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            List<Document> documents = reader.get();
            // 对文档进行分词和分割处理
            List<Document> documentSpliterList = tokenTextSplitter.apply(documents);

            // 为原始文档和分割后的文档添加知识库标签元数据
            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSpliterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            // 将处理后的文档存储到向量数据库中
            pgVectorStore.accept(documentSpliterList);

            // 维护Redis中的标签列表，确保标签唯一性
            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }

            log.info("upload files end: {}", ragTag);
        }
        return Response.<String>builder()
                .code("0000")
                .info("上传成功")
                .build();
    }

}
