package com.github.soyoger.modelgateway.application.service;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ApiKeyCredential;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import com.github.soyoger.modelgateway.domain.service.AuthService;
import com.github.soyoger.modelgateway.domain.service.CanaryService;
import com.github.soyoger.modelgateway.domain.service.RateLimitService;
import com.github.soyoger.modelgateway.domain.service.RouteService;
import com.github.soyoger.modelgateway.domain.service.StainService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 网关核心编排应用服务。
 */
@RequiredArgsConstructor
public class GatewayService {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final StainService stainService;
    private final CanaryService canaryService;
    private final RouteService routeService;

    public Mono<RouteResult> process(GatewayRequest request) {
        return Mono.fromCallable(() -> {
            // 1. 认证
            ApiKeyCredential credential = authService.authenticate(extractApiKey(request));
            request.setCredential(credential);

            // 2. 模型权限校验
            authService.authorizeModel(credential, request.getModel());

            // 3. 限流
            if (!rateLimitService.tryAcquire(request)) {
                throw new GatewayException(GatewayErrorCode.RATE_LIMITED,
                    "Rate limit exceeded", request.getRequestId());
            }

            // 4. 染色体染色
            stainService.applyStains(request);

            // 5. 灰度判定
            String targetVersion = canaryService.targetVersion(request);
            if (targetVersion != null) {
                request.setContextValue("targetVersion", targetVersion);
            }

            // 6. 路由
            return routeService.route(request);
        });
    }

    private String extractApiKey(GatewayRequest request) {
        String auth = request.getHeaders() != null ? request.getHeaders().get("Authorization") : null;
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return request.getHeaders() != null ? request.getHeaders().get("X-API-Key") : null;
    }
}
