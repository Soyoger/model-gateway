package com.github.soyoger.modelgateway.infrastructure.rate;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.service.RateLimitService;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Resilience4j 的本地内存限流器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Resilience4jRateLimiter implements RateLimitService {

    private final GatewayProperties properties;

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(GatewayRequest request) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String key = resolveKey(request);
        RateLimiter limiter = limiters.computeIfAbsent(key, this::createLimiter);
        boolean allowed = limiter.acquirePermission();
        if (log.isDebugEnabled()) {
            log.debug("Rate limit check key={}, allowed={}, available={}",
                key, allowed, limiter.getMetrics().getAvailablePermissions());
        }
        return allowed;
    }

    private String resolveKey(GatewayRequest request) {
        String apiKey = request.getCredential() != null ? request.getCredential().getKey() : "anonymous";
        String model = request.getModel() != null ? request.getModel() : "default";
        return apiKey + ":" + model;
    }

    private RateLimiter createLimiter(String key) {
        GatewayProperties.RateLimitConfig config = properties.getRateLimit();
        RateLimiterConfig limiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(config.getLimitForPeriod())
            .limitRefreshPeriod(config.getLimitRefreshPeriod())
            .timeoutDuration(config.getTimeoutDuration())
            .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(limiterConfig);
        RateLimiter limiter = registry.rateLimiter(key);
        if (config.isSubscribeForEvents()) {
            limiter.getEventPublisher()
                .onSuccess(event -> log.debug("Rate limit success: {}", event.getRateLimiterName()))
                .onFailure(event -> log.warn("Rate limit failure: {}", event.getRateLimiterName()));
        }
        return limiter;
    }
}
