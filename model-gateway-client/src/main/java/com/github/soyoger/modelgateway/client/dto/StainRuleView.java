package com.github.soyoger.modelgateway.client.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Admin 染色体规则视图。
 */
@Data
public class StainRuleView {

    private String ruleId;
    private String stainKey;
    private String model;
    private Integer samplingRate;
    private Map<String, String> headerMatch;
    private Boolean forceRoute;
    private List<String> targetEndpointIds;
}
