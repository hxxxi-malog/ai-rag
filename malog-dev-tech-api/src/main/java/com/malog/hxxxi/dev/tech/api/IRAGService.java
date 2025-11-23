package com.malog.hxxxi.dev.tech.api;

import com.malog.hxxxi.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG服务接口
 * 提供RAG标签查询和文件上传功能
 */
public interface IRAGService {

    /**
     * 查询RAG标签列表
     *
     * @return 包含标签列表的响应对象
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传文件到指定的RAG标签下
     *
     * @param ragTag RAG标签名称，用于标识文件所属的分类
     * @param files 要上传的文件列表，支持多个文件同时上传
     * @return 包含上传结果信息的响应对象
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

}
