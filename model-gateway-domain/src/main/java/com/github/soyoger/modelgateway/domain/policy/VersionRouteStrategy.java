package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.RouteReason;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 按版本号优先路由策略。
 */
public class VersionRouteStrategy implements RouteStrategy {

    @Override
    public RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates) {
        String targetVersion = request.getContextValue("targetVersion");
        if (targetVersion == null || targetVersion.isEmpty()) {
            return null;
        }
        List<ModelEndpoint> matched = candidates.stream()
            .filter(endpoint -> targetVersion.equals(endpoint.getVersion()))
            .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return null;
        }
        return RouteResult.builder()
            .endpoint(matched.get(0))
            .reason(RouteReason.VERSION)
            .build();
    }

    @Override
    public String name() {
        return "version";
    }
}
