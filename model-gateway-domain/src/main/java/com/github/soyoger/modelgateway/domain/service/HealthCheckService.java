package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;

/**
 * 健康检查领域服务接口。
 */
public interface HealthCheckService {

    /**
     * 检查单个节点健康状况。
     *
     * @param endpoint 后端节点
     * @return true 表示健康
     */
    boolean check(ModelEndpoint endpoint);
}
