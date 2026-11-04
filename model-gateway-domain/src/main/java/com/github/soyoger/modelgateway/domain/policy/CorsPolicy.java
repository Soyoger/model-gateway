package com.github.soyoger.modelgateway.domain.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 跨域策略。
 */
@Data
@Builder
public class CorsPolicy {

    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials;
    private long maxAge;
}
