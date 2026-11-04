package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.RouteReason;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 染色体路由策略。
 */
public class StainRouteStrategy implements RouteStrategy {

    @Override
    public RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates) {
        if (request.getStains() == null || request.getStains().isEmpty()) {
            return null;
        }
        for (String stain : request.getStains().keySet()) {
            List<ModelEndpoint> matched = candidates.stream()
                .filter(endpoint -> endpoint.supportsStain(stain))
                .collect(Collectors.toList());
            if (!matched.isEmpty()) {
                return RouteResult.builder()
                    .endpoint(matched.get(0))
                    .reason(RouteReason.STAIN)
                    .stainHit(stain)
                    .build();
            }
        }
        return null;
    }

    @Override
    public String name() {
        return "stain";
    }
}
