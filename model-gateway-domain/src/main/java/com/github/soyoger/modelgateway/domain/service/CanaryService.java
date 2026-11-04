package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.policy.CanaryRule;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 灰度领域服务。
 */
@RequiredArgsConstructor
public class CanaryService {

    private final RouteConfigRepository routeConfigRepository;

    public boolean isCanary(GatewayRequest request) {
        List<CanaryRule> rules = routeConfigRepository.loadCanaryRules();
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        return rules.stream()
            .filter(rule -> Objects.equals(rule.getModel(), request.getModel()))
            .anyMatch(rule -> match(rule, request));
    }

    public String targetVersion(GatewayRequest request) {
        List<CanaryRule> rules = routeConfigRepository.loadCanaryRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (CanaryRule rule : rules) {
            if (Objects.equals(rule.getModel(), request.getModel()) && match(rule, request)) {
                return rule.getTargetVersion();
            }
        }
        return null;
    }

    private boolean match(CanaryRule rule, GatewayRequest request) {
        String userId = request.getCredential() != null ? request.getCredential().getUserId() : null;
        if (rule.getUserWhitelist() != null && userId != null && rule.getUserWhitelist().contains(userId)) {
            return true;
        }
        if (matchMap(rule.getHeaderMatch(), request.getHeaders())) {
            return true;
        }
        if (matchMap(rule.getCookieMatch(), request.getHeaders())) {
            return true;
        }
        int percentage = rule.getPercentage();
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }
        int hash = Math.abs(hash(request.getRequestId()));
        return hash % 100 < percentage;
    }

    private boolean matchMap(Map<String, String> ruleMap, Map<String, String> requestMap) {
        if (ruleMap == null || ruleMap.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : ruleMap.entrySet()) {
            String value = requestMap != null ? requestMap.get(entry.getKey()) : null;
            if (!Objects.equals(value, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private int hash(String input) {
        if (input == null) {
            return 0;
        }
        return input.hashCode();
    }
}
