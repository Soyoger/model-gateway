package com.github.soyoger.modelgateway.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网关配置属性。
 */
@Data
@ConfigurationProperties(prefix = "model-gateway")
public class GatewayProperties {

    private List<ApiKeyConfig> apiKeys = new ArrayList<>();
    private List<RouteConfig> routes = new ArrayList<>();
    private List<CanaryRuleConfig> canaryRules = new ArrayList<>();
    private List<StainRuleConfig> stainRules = new ArrayList<>();
    private ForwardConfig forward = new ForwardConfig();
    private CorsConfig cors = new CorsConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private WebSocketConfig websocket = new WebSocketConfig();

    @Data
    public static class ApiKeyConfig {
        private String key;
        private String tenant;
        private String userId;
        private Set<String> allowedModels = new HashSet<>();
        private Long rateLimitQps;
        private boolean enabled = true;
    }

    @Data
    public static class RouteConfig {
        private String model;
        private String strategy = "composite";
        private List<EndpointConfig> endpoints = new ArrayList<>();
    }

    @Data
    public static class EndpointConfig {
        private String id;
        private String version;
        private String baseUrl;
        private int weight = 100;
        private boolean canary;
        private Map<String, String> tags = new HashMap<>();
        private Set<String> stains = new HashSet<>();
    }

    @Data
    public static class CanaryRuleConfig {
        private String ruleId;
        private String model;
        private int percentage;
        private String targetVersion;
        private List<String> userWhitelist = new ArrayList<>();
        private Map<String, String> headerMatch = new HashMap<>();
        private Map<String, String> cookieMatch = new HashMap<>();
    }

    @Data
    public static class StainRuleConfig {
        private String ruleId;
        private String stainKey;
        private String model;
        private int samplingRate;
        private Map<String, String> headerMatch = new HashMap<>();
        private boolean forceRoute;
        private List<String> targetEndpointIds = new ArrayList<>();
    }

    @Data
    public static class ForwardConfig {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Duration writeTimeout = Duration.ofSeconds(60);
        private long maxResponseSize = 16 * 1024 * 1024;
        private int retryCount = 1;
    }

    @Data
    public static class CorsConfig {
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = List.of("*");
        private List<String> allowedHeaders = List.of("*");
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials;
        private long maxAge = 3600;
    }

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private long defaultQps = 100;
        private int limitForPeriod = 100;
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private Duration timeoutDuration = Duration.ZERO;
        private boolean subscribeForEvents = false;
    }

    @Data
    public static class WebSocketConfig {
        private boolean heartbeatEnabled = true;
        private Duration heartbeatInterval = Duration.ofSeconds(30);
        private Duration idleTimeout = Duration.ofSeconds(120);
        private boolean backendHeartbeatEnabled = true;
    }
}
