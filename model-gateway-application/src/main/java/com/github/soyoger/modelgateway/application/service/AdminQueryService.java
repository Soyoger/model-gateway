package com.github.soyoger.modelgateway.application.service;

import com.github.soyoger.modelgateway.client.dto.CanaryRuleView;
import com.github.soyoger.modelgateway.client.dto.EndpointHealthView;
import com.github.soyoger.modelgateway.client.dto.RouteView;
import com.github.soyoger.modelgateway.client.dto.StainRuleView;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.repository.EndpointRepository;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin 查询应用服务。
 */
@RequiredArgsConstructor
public class AdminQueryService {

    private final EndpointRepository endpointRepository;
    private final RouteConfigRepository routeConfigRepository;

    public List<RouteView> listRoutes() {
        List<ModelEndpoint> all = endpointRepository.findAll();
        return all.stream()
            .collect(Collectors.groupingBy(ModelEndpoint::getModel))
            .entrySet().stream()
            .map(entry -> {
                RouteView view = new RouteView();
                view.setModel(entry.getKey());
                view.setStrategy("composite");
                view.setEndpoints(entry.getValue().stream().map(this::toEndpointView).collect(Collectors.toList()));
                return view;
            })
            .collect(Collectors.toList());
    }

    public List<EndpointHealthView> listEndpointHealth() {
        return endpointRepository.findAll().stream()
            .map(endpoint -> {
                EndpointHealthView view = new EndpointHealthView();
                view.setEndpointId(endpoint.getId());
                view.setModel(endpoint.getModel());
                view.setBaseUrl(endpoint.getBaseUrl().toString());
                view.setStatus(endpoint.getStatus().name());
                return view;
            })
            .collect(Collectors.toList());
    }

    public List<CanaryRuleView> listCanaryRules() {
        return routeConfigRepository.loadCanaryRules().stream()
            .map(rule -> {
                CanaryRuleView view = new CanaryRuleView();
                view.setRuleId(rule.getRuleId());
                view.setModel(rule.getModel());
                view.setPercentage(rule.getPercentage());
                view.setTargetVersion(rule.getTargetVersion());
                view.setUserWhitelist(rule.getUserWhitelist());
                view.setHeaderMatch(rule.getHeaderMatch());
                view.setCookieMatch(rule.getCookieMatch());
                return view;
            })
            .collect(Collectors.toList());
    }

    public List<StainRuleView> listStainRules() {
        return routeConfigRepository.loadStainRules().stream()
            .map(rule -> {
                StainRuleView view = new StainRuleView();
                view.setRuleId(rule.getRuleId());
                view.setStainKey(rule.getStainKey());
                view.setModel(rule.getModel());
                view.setSamplingRate(rule.getSamplingRate());
                view.setHeaderMatch(rule.getHeaderMatch());
                view.setForceRoute(rule.isForceRoute());
                view.setTargetEndpointIds(rule.getTargetEndpointIds());
                return view;
            })
            .collect(Collectors.toList());
    }

    public void refreshRoutes() {
        routeConfigRepository.refresh();
    }

    private RouteView.EndpointView toEndpointView(ModelEndpoint endpoint) {
        RouteView.EndpointView view = new RouteView.EndpointView();
        view.setId(endpoint.getId());
        view.setVersion(endpoint.getVersion());
        view.setBaseUrl(endpoint.getBaseUrl().toString());
        view.setWeight(endpoint.getWeight());
        view.setCanary(endpoint.isCanary());
        view.setStains(endpoint.getStains() != null ? new ArrayList<>(endpoint.getStains()) : new ArrayList<>());
        view.setStatus(endpoint.getStatus().name());
        return view;
    }
}
