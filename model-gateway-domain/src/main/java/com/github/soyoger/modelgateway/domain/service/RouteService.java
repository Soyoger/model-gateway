package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import com.github.soyoger.modelgateway.domain.policy.CompositeRouteStrategy;
import com.github.soyoger.modelgateway.domain.policy.RouteStrategy;
import com.github.soyoger.modelgateway.domain.repository.EndpointRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 路由领域服务。
 */
@RequiredArgsConstructor
public class RouteService {

    private final EndpointRepository endpointRepository;
    private final RouteStrategy routeStrategy;

    public RouteService(EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
        this.routeStrategy = new CompositeRouteStrategy();
    }

    public RouteResult route(GatewayRequest request) {
        List<ModelEndpoint> candidates = endpointRepository.findByModel(request.getModel());
        if (candidates == null || candidates.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.NO_AVAILABLE_ENDPOINT,
                "No endpoint available for model: " + request.getModel(), request.getRequestId());
        }
        List<ModelEndpoint> healthy = candidates.stream()
            .filter(ModelEndpoint::isHealthy)
            .collect(Collectors.toList());
        if (healthy.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.NO_AVAILABLE_ENDPOINT,
                "No healthy endpoint available for model: " + request.getModel(), request.getRequestId());
        }
        RouteResult result = routeStrategy.route(request, healthy);
        if (result == null || result.getEndpoint() == null) {
            throw new GatewayException(GatewayErrorCode.ROUTE_FAILED,
                "Route failed for model: " + request.getModel(), request.getRequestId());
        }
        return result;
    }
}
