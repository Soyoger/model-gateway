package com.github.soyoger.modelgateway.infrastructure.schedule;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.EndpointStatus;
import com.github.soyoger.modelgateway.domain.repository.EndpointRepository;
import com.github.soyoger.modelgateway.domain.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 后端节点健康检查定时任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckScheduler {

    private final EndpointRepository endpointRepository;
    private final HealthCheckService healthCheckService;

    @Scheduled(fixedDelay = 30000)
    public void checkAll() {
        List<ModelEndpoint> endpoints = endpointRepository.findAll();
        for (ModelEndpoint endpoint : endpoints) {
            boolean healthy = healthCheckService.check(endpoint);
            EndpointStatus newStatus = healthy ? EndpointStatus.HEALTHY : EndpointStatus.UNHEALTHY;
            if (endpoint.getStatus() != newStatus) {
                log.info("Endpoint {} status changed from {} to {}", endpoint.getId(), endpoint.getStatus(), newStatus);
                endpoint.setStatus(newStatus);
                endpointRepository.save(endpoint);
            }
        }
    }
}
