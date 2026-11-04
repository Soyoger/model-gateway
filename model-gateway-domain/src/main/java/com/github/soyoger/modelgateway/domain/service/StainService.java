package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.policy.StainRule;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 染色体领域服务。
 */
@RequiredArgsConstructor
public class StainService {

    private final RouteConfigRepository routeConfigRepository;
    private final Random random = new SecureRandom();

    public void applyStains(GatewayRequest request) {
        List<StainRule> rules = routeConfigRepository.loadStainRules();
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (StainRule rule : rules) {
            if (!Objects.equals(rule.getModel(), request.getModel())) {
                continue;
            }
            if (request.hasStain(rule.getStainKey())) {
                continue;
            }
            if (match(rule, request)) {
                request.addStain(rule.getStainKey(), rule.getRuleId());
            }
        }
    }

    private boolean match(StainRule rule, GatewayRequest request) {
        if (matchMap(rule.getHeaderMatch(), request.getHeaders())) {
            return true;
        }
        int rate = rule.getSamplingRate();
        if (rate <= 0) {
            return false;
        }
        if (rate >= 100) {
            return true;
        }
        return random.nextInt(100) < rate;
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
}
