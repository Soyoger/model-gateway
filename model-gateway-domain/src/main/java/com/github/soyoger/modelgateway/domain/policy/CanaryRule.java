package com.github.soyoger.modelgateway.domain.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 灰度规则。
 */
@Data
@Builder
public class CanaryRule {

    private String ruleId;
    private String model;
    private int percentage;
    private String targetVersion;
    private List<String> userWhitelist;
    private Map<String, String> headerMatch;
    private Map<String, String> cookieMatch;
}
