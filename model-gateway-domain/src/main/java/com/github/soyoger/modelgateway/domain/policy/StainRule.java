package com.github.soyoger.modelgateway.domain.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 染色体规则。
 */
@Data
@Builder
public class StainRule {

    private String ruleId;
    private String stainKey;
    private String model;
    private int samplingRate;
    private Map<String, String> headerMatch;
    private boolean forceRoute;
    private List<String> targetEndpointIds;
}
