package com.github.soyoger.modelgateway.domain.entity;

import com.github.soyoger.modelgateway.domain.enums.EndpointStatus;
import lombok.Builder;
import lombok.Data;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * 模型后端节点实体。
 */
@Data
@Builder
public class ModelEndpoint {

    private String id;
    private String model;
    private String version;
    private URI baseUrl;
    private Map<String, String> tags;
    private int weight;
    private boolean canary;
    private Set<String> stains;
    private EndpointStatus status;

    public boolean isHealthy() {
        return status == EndpointStatus.HEALTHY || status == EndpointStatus.UNKNOWN;
    }

    public boolean supportsStain(String stain) {
        return stains != null && stains.contains(stain);
    }
}
