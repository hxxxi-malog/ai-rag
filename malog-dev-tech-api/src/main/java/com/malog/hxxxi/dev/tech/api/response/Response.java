package com.malog.hxxxi.dev.tech.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Auth : Malog
 * @Desc : 通用响应结果封装类，用于统一接口返回格式
 * @Time : 2025/11/23 20:30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    /** 响应状态码 */
    private String code;

    /** 响应信息描述 */
    private String info;

    /** 响应数据内容 */
    private T data;

}

