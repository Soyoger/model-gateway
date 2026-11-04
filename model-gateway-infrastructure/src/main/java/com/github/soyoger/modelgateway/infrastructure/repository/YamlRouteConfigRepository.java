package com.github.soyoger.modelgateway.infrastructure.repository;

import com.github.soyoger.modelgateway.domain.policy.CanaryRule;
import com.github.soyoger.modelgateway.domain.policy.CorsPolicy;
import com.github.soyoger.modelgateway.domain.policy.RetryPolicy;
import com.github.soyoger.modelgateway.domain.policy.StainRule;
import com.github.soyoger.modelgateway.domain.policy.TimeoutPolicy;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * YAML 路由配置仓储。
 */
@Repository
@RequiredArgsConstructor
public class YamlRouteConfigRepository implements RouteConfigRepository {

    private final GatewayProperties properties;
    private final InMemoryEndpointRepository endpointRepository;
    private final InMemoryApiKeyRepository apiKeyRepository;

    @Override
    public void refresh() {
        endpointRepository.refresh();
        apiKeyRepository.refresh();
    }

    @Override
    public List<CanaryRule> loadCanaryRules() {
        if (properties.getCanaryRules() == null) {
            return List.of();
        }
        return properties.getCanaryRules().stream()
            .map(config -> CanaryRule.builder()
                .ruleId(config.getRuleId())
                .model(config.getModel())
                .percentage(config.getPercentage())
                .targetVersion(config.getTargetVersion())
                .userWhitelist(config.getUserWhitelist())
                .headerMatch(config.getHeaderMatch())
                .cookieMatch(config.getCookieMatch())
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public List<StainRule> loadStainRules() {
        if (properties.getStainRules() == null) {
            return List.of();
        }
        return properties.getStainRules().stream()
            .map(config -> StainRule.builder()
                .ruleId(config.getRuleId())
                .stainKey(config.getStainKey())
                .model(config.getModel())
                .samplingRate(config.getSamplingRate())
                .headerMatch(config.getHeaderMatch())
                .forceRoute(config.isForceRoute())
                .targetEndpointIds(config.getTargetEndpointIds())
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public TimeoutPolicy loadTimeoutPolicy() {
        GatewayProperties.ForwardConfig forward = properties.getForward();
        return TimeoutPolicy.builder()
            .connectTimeout(forward.getConnectTimeout())
            .readTimeout(forward.getReadTimeout())
            .writeTimeout(forward.getWriteTimeout())
            .maxResponseSize(forward.getMaxResponseSize())
            .build();
    }

    @Override
    public RetryPolicy loadRetryPolicy() {
        return RetryPolicy.builder()
            .maxRetries(properties.getForward().getRetryCount())
            .retryableMethods(Set.of("GET"))
            .retryableStatusCodes(Set.of(500, 502, 503, 504))
            .build();
    }

    @Override
    public CorsPolicy loadCorsPolicy() {
        GatewayProperties.CorsConfig cors = properties.getCors();
        return CorsPolicy.builder()
            .allowedOrigins(cors.getAllowedOrigins())
            .allowedMethods(cors.getAllowedMethods())
            .allowedHeaders(cors.getAllowedHeaders())
            .exposedHeaders(cors.getExposedHeaders())
            .allowCredentials(cors.isAllowCredentials())
            .maxAge(cors.getMaxAge())
            .build();
    }
}
