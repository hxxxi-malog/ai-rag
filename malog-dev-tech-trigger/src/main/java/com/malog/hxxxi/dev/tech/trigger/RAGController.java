package com.malog.hxxxi.dev.tech.trigger;

import com.malog.hxxxi.dev.tech.api.IRAGService;
import com.malog.hxxxi.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
     * - code: 响应码，"0000"表示成功
     * - info: 响应信息，"上传成功"表示操作结果
     * - data: 标签列表数据
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
     * @param files  待上传的文件列表
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

    /**
     * 分析指定的 Git 仓库，将其内容提取并存储到向量数据库中。
     * <p>
     * 该方法会克隆指定 URL 的 Git 仓库到本地临时目录，然后递归遍历所有文件，
     * 使用 Tika 解析文档内容，并进行分词处理后存入向量数据库（pgVectorStore）。
     * 同时将项目名称记录至 Redis 列表中用于后续检索或标记。
     *
     * @param repoUrl  Git 仓库地址，必须是可访问的 HTTPS 或 SSH 地址
     * @param userName 访问 Git 仓库所需的用户名（如 GitHub 用户名）
     * @param token    访问 Git 仓库所需的认证令牌（如 GitHub Personal Access Token）
     * @return 响应对象，表示操作是否成功。若成功则 code 为 "0000"，info 为 "调用成功"
     * @throws Exception 克隆、读取文件或解析过程中可能抛出异常
     */
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(@RequestParam("repoUrl") String repoUrl, @RequestParam("userName") String userName, @RequestParam("token") String token) throws Exception {
        // 定义本地克隆路径及从URL中提取项目名
        String localPath = "./git-cloned-repo";
        String repoProjectName = _extractProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        // 删除旧的克隆目录以确保干净环境
        FileUtils.deleteDirectory(new File(localPath));

        // 克隆远程 Git 仓库到本地
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .call();

        // 遍历克隆后的文件夹中的所有文件并处理
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());
                try {
                    // 使用 Tika 解析文件内容
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    // 添加元数据标识所属知识库
                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                    // 存储到向量数据库
                    pgVectorStore.accept(documentSplitterList);
                } catch (Exception e) {
                    log.error("遍历解析路径，上传知识库失败:{}", file.getFileName());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.info("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        // 清理本地克隆的仓库目录
        FileUtils.deleteDirectory(new File(localPath));

        // 将项目名称加入 Redis 标签列表中，避免重复添加
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(repoProjectName)) {
            elements.add(repoProjectName);
        }

        // 关闭 Git 资源连接
        git.close();

        log.info("遍历解析路径，上传完成:{}", repoUrl);

        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    /**
     * 从 Git 仓库 URL 中提取项目名称。
     * <p>
     * 示例输入："https://github.com/user/repo.git"，输出："repo"
     *
     * @param repoUrl Git 仓库的完整 URL
     * @return 提取出的项目名称（不含 .git 扩展名）
     */
    private String _extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }


}
