package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.RouteReason;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 按标签匹配路由策略。
 */
public class TagRouteStrategy implements RouteStrategy {

    @Override
    public RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates) {
        Map<String, String> requestTags = request.getContextValue("tags");
        if (requestTags == null || requestTags.isEmpty()) {
            return null;
        }
        List<ModelEndpoint> matched = candidates.stream()
            .filter(endpoint -> matches(endpoint.getTags(), requestTags))
            .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return null;
        }
        return RouteResult.builder()
            .endpoint(matched.get(0))
            .reason(RouteReason.TAG)
            .build();
    }

    private boolean matches(Map<String, String> endpointTags, Map<String, String> requestTags) {
        if (endpointTags == null || endpointTags.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : requestTags.entrySet()) {
            String value = endpointTags.get(entry.getKey());
            if (value == null || !value.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String name() {
        return "tag";
    }
}
