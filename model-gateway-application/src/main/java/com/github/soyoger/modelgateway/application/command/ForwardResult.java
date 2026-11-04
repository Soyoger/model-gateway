package com.github.soyoger.modelgateway.application.command;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 转发结果。
 *
 * @param <T> 响应体类型：Mono/Flux 或 byte[]
 */
@Data
@Builder
public class ForwardResult<T> {

    private int statusCode;
    private Map<String, List<String>> headers;
    private T body;
    private boolean streaming;
}
