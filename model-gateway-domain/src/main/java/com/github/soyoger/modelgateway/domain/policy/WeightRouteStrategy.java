package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.RouteReason;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 按权重随机路由策略。
 */
public class WeightRouteStrategy implements RouteStrategy {

    private final Random random = new SecureRandom();

    @Override
    public RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        int totalWeight = candidates.stream().mapToInt(ModelEndpoint::getWeight).sum();
        if (totalWeight <= 0) {
            return firstCandidate(candidates);
        }
        int hit = random.nextInt(totalWeight);
        int current = 0;
        for (ModelEndpoint endpoint : candidates) {
            current += endpoint.getWeight();
            if (hit < current) {
                return RouteResult.builder()
                    .endpoint(endpoint)
                    .reason(RouteReason.WEIGHT)
                    .build();
            }
        }
        return firstCandidate(candidates);
    }

    private RouteResult firstCandidate(List<ModelEndpoint> candidates) {
        return RouteResult.builder()
            .endpoint(candidates.get(0))
            .reason(RouteReason.FALLBACK)
            .build();
    }

    @Override
    public String name() {
        return "weight";
    }
}
