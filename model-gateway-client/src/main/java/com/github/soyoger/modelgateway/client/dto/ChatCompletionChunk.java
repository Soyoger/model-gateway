package com.github.soyoger.modelgateway.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容的 Chat Completion SSE 流式块。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        private Integer index;
        private Delta delta;
        private String finishReason;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        private String role;
        private String content;
    }
}
