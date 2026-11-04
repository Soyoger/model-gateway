package com.github.soyoger.modelgateway.domain.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * 转发超时策略。
 */
@Data
@Builder
public class TimeoutPolicy {

    private Duration connectTimeout;
    private Duration readTimeout;
    private Duration writeTimeout;
    private long maxResponseSize;
}
