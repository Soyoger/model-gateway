package com.github.soyoger.modelgateway.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * API Key 凭证实体。
 */
@Data
@Builder
public class ApiKeyCredential {

    private String key;
    private String tenant;
    private String userId;
    private Set<String> allowedModels;
    private Long rateLimitQps;
    private boolean enabled;

    public boolean canAccessModel(String model) {
        if (allowedModels == null || allowedModels.isEmpty()) {
            return true;
        }
        return allowedModels.contains(model);
    }
}
