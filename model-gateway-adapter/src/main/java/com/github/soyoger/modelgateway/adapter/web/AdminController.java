package com.github.soyoger.modelgateway.adapter.web;

import com.github.soyoger.modelgateway.application.service.AdminQueryService;
import com.github.soyoger.modelgateway.client.dto.CanaryRuleView;
import com.github.soyoger.modelgateway.client.dto.EndpointHealthView;
import com.github.soyoger.modelgateway.client.dto.RouteView;
import com.github.soyoger.modelgateway.client.dto.StainRuleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin 管理接口。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "网关管理接口")
public class AdminController {

    private final AdminQueryService adminQueryService;

    @Operation(summary = "查看路由表")
    @GetMapping("/routes")
    public Mono<List<RouteView>> listRoutes() {
        return Mono.just(adminQueryService.listRoutes());
    }

    @Operation(summary = "刷新路由配置")
    @PostMapping("/routes/refresh")
    public Mono<Void> refreshRoutes() {
        return Mono.fromRunnable(adminQueryService::refreshRoutes);
    }

    @Operation(summary = "查看节点健康状态")
    @GetMapping("/endpoints/health")
    public Mono<List<EndpointHealthView>> listEndpointHealth() {
        return Mono.just(adminQueryService.listEndpointHealth());
    }

    @Operation(summary = "查看灰度规则")
    @GetMapping("/canary/rules")
    public Mono<List<CanaryRuleView>> listCanaryRules() {
        return Mono.just(adminQueryService.listCanaryRules());
    }

    @Operation(summary = "查看染色体规则")
    @GetMapping("/stain/rules")
    public Mono<List<StainRuleView>> listStainRules() {
        return Mono.just(adminQueryService.listStainRules());
    }
}
