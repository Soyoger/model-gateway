package com.github.soyoger.modelgateway.domain.policy;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;

import java.util.List;

/**
 * 路由策略接口。
 */
public interface RouteStrategy {

    /**
     * 根据请求从候选节点中选择目标节点。
     *
     * @param request    网关请求
     * @param candidates 候选节点（已过滤健康状态）
     * @return 路由结果，若无法路由则返回 null
     */
    RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates);

    /**
     * 策略名称。
     */
    String name();
}
