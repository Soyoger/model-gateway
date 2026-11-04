package com.github.soyoger.modelgateway.client.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Admin 灰度规则视图。
 */
@Data
public class CanaryRuleView {

    private String ruleId;
    private String model;
    private Integer percentage;
    private String targetVersion;
    private List<String> userWhitelist;
    private Map<String, String> headerMatch;
    private Map<String, String> cookieMatch;
}
