package com.github.soyoger.modelgateway.domain.aggregate;

import com.github.soyoger.modelgateway.domain.entity.ApiKeyCredential;
import com.github.soyoger.modelgateway.domain.enums.ProtocolType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关统一请求聚合对象。
 */
@Data
@Builder
public class GatewayRequest {

    private String requestId;
    private ProtocolType protocol;
    private String method;
    private String path;
    private String model;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;
    private ApiKeyCredential credential;

    @Builder.Default
    private Map<String, String> stains = new HashMap<>();

    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    public void addStain(String key, String value) {
        stains.put(key, value);
    }

    public String getStainValue(String key) {
        return stains.get(key);
    }

    public boolean hasStain(String key) {
        return stains.containsKey(key);
    }

    public void setContextValue(String key, Object value) {
        context.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key) {
        return (T) context.get(key);
    }
}
