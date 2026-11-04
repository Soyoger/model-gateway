package com.github.soyoger.modelgateway.domain.repository;

import com.github.soyoger.modelgateway.domain.policy.CanaryRule;
import com.github.soyoger.modelgateway.domain.policy.CorsPolicy;
import com.github.soyoger.modelgateway.domain.policy.RetryPolicy;
import com.github.soyoger.modelgateway.domain.policy.StainRule;
import com.github.soyoger.modelgateway.domain.policy.TimeoutPolicy;

import java.util.List;

/**
 * 路由配置仓储接口。
 */
public interface RouteConfigRepository {

    void refresh();

    List<CanaryRule> loadCanaryRules();

    List<StainRule> loadStainRules();

    TimeoutPolicy loadTimeoutPolicy();

    RetryPolicy loadRetryPolicy();

    CorsPolicy loadCorsPolicy();
}
