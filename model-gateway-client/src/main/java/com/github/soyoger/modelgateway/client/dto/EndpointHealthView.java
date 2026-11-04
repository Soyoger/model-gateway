package com.github.soyoger.modelgateway.client.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Admin 后端节点健康视图。
 */
@Data
public class EndpointHealthView {

    private String endpointId;
    private String model;
    private String baseUrl;
    private String status;
    private String lastCheckResult;
    private Instant lastCheckTime;
}
