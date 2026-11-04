package com.github.soyoger.modelgateway.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容的 Chat Completion 请求体。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

    private String model;
    private List<Message> messages;
    private Boolean stream;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer n;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private String content;
    }
}
