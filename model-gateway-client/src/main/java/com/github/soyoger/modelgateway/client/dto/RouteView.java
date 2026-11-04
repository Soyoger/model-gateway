package com.github.soyoger.modelgateway.client.dto;

import lombok.Data;

import java.util.List;

/**
 * Admin 路由表视图。
 */
@Data
public class RouteView {

    private String model;
    private String strategy;
    private List<EndpointView> endpoints;

    @Data
    public static class EndpointView {
        private String id;
        private String version;
        private String baseUrl;
        private Integer weight;
        private Boolean canary;
        private List<String> stains;
        private String status;
    }
}
