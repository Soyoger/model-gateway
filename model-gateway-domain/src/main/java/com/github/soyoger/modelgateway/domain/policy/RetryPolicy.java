package com.github.soyoger.modelgateway.domain.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.Set;

/**
 * 重试策略。
 */
@Data
@Builder
public class RetryPolicy {

    private int maxRetries;
    private Duration backoff;
    private Set<String> retryableMethods;
    private Set<Integer> retryableStatusCodes;
}
