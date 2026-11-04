package com.github.soyoger.modelgateway.infrastructure.repository;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.EndpointStatus;
import com.github.soyoger.modelgateway.domain.repository.EndpointRepository;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存版后端节点仓储。
 */
@Repository
@RequiredArgsConstructor
public class InMemoryEndpointRepository implements EndpointRepository {

    private final GatewayProperties properties;
    private final Map<String, ModelEndpoint> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        store.clear();
        if (properties.getRoutes() == null) {
            return;
        }
        for (GatewayProperties.RouteConfig route : properties.getRoutes()) {
            if (route.getEndpoints() == null) {
                continue;
            }
            for (GatewayProperties.EndpointConfig endpoint : route.getEndpoints()) {
                ModelEndpoint modelEndpoint = ModelEndpoint.builder()
                    .id(endpoint.getId())
                    .model(route.getModel())
                    .version(endpoint.getVersion())
                    .baseUrl(URI.create(endpoint.getBaseUrl()))
                    .tags(endpoint.getTags())
                    .weight(endpoint.getWeight())
                    .canary(endpoint.isCanary())
                    .stains(endpoint.getStains())
                    .status(EndpointStatus.UNKNOWN)
                    .build();
                store.put(endpoint.getId(), modelEndpoint);
            }
        }
    }

    @Override
    public List<ModelEndpoint> findByModel(String model) {
        return store.values().stream()
            .filter(endpoint -> endpoint.getModel().equals(model))
            .collect(Collectors.toList());
    }

    @Override
    public List<ModelEndpoint> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<ModelEndpoint> findById(String endpointId) {
        return Optional.ofNullable(store.get(endpointId));
    }

    @Override
    public void save(ModelEndpoint endpoint) {
        store.put(endpoint.getId(), endpoint);
    }
}
