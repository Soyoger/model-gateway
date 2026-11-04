package com.github.soyoger.modelgateway.infrastructure.repository;

import com.github.soyoger.modelgateway.domain.entity.ApiKeyCredential;
import com.github.soyoger.modelgateway.domain.repository.ApiKeyRepository;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 API Key 仓储。
 */
@Repository
@RequiredArgsConstructor
public class InMemoryApiKeyRepository implements ApiKeyRepository {

    private final GatewayProperties properties;
    private final Map<String, ApiKeyCredential> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        store.clear();
        if (properties.getApiKeys() == null) {
            return;
        }
        for (GatewayProperties.ApiKeyConfig config : properties.getApiKeys()) {
            ApiKeyCredential credential = ApiKeyCredential.builder()
                .key(config.getKey())
                .tenant(config.getTenant())
                .userId(config.getUserId())
                .allowedModels(config.getAllowedModels())
                .rateLimitQps(config.getRateLimitQps())
                .enabled(config.isEnabled())
                .build();
            store.put(config.getKey(), credential);
        }
    }

    @Override
    public Optional<ApiKeyCredential> findByKey(String apiKey) {
        return Optional.ofNullable(store.get(apiKey));
    }
}
