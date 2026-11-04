package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;

/**
 * 限流领域服务接口。
 */
public interface RateLimitService {

    /**
     * 判断是否允许通过。
     *
     * @param request 网关请求
     * @return true 表示允许通过
     */
    boolean tryAcquire(GatewayRequest request);
}
