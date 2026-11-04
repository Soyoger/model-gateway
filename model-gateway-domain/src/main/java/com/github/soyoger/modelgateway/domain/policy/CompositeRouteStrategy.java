package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合路由策略。
 *
 * 优先级：染色体 > 灰度版本 > 标签 > 权重。
 */
public class CompositeRouteStrategy implements RouteStrategy {

    private final List<RouteStrategy> strategies = new ArrayList<>();
    private final RouteStrategy fallback = new WeightRouteStrategy();

    public CompositeRouteStrategy() {
        strategies.add(new StainRouteStrategy());
        strategies.add(new VersionRouteStrategy());
        strategies.add(new TagRouteStrategy());
    }

    @Override
    public RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates) {
        for (RouteStrategy strategy : strategies) {
            RouteResult result = strategy.route(request, candidates);
            if (result != null) {
                return result;
            }
        }
        return fallback.route(request, candidates);
    }

    @Override
    public String name() {
        return "composite";
    }
}
