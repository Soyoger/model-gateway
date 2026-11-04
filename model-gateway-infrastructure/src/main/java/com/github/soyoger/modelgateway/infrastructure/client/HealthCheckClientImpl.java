package com.github.soyoger.modelgateway.infrastructure.client;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 健康检查客户端实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckClientImpl implements HealthCheckService {

    private final WebClientFactory webClientFactory;

    @Override
    public boolean check(ModelEndpoint endpoint) {
        try {
            WebClient webClient = webClientFactory.create();
            webClient.get()
                .uri(endpoint.getBaseUrl() + "/health")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
            return true;
        } catch (Exception e) {
            log.warn("Health check failed for endpoint {}: {}", endpoint.getId(), e.getMessage());
            return false;
        }
    }
}
