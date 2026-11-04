package com.github.soyoger.modelgateway.infrastructure.config;

import com.github.soyoger.modelgateway.application.service.AdminQueryService;
import com.github.soyoger.modelgateway.application.service.GatewayService;
import com.github.soyoger.modelgateway.domain.repository.ApiKeyRepository;
import com.github.soyoger.modelgateway.domain.repository.EndpointRepository;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import com.github.soyoger.modelgateway.domain.service.AuthService;
import com.github.soyoger.modelgateway.domain.service.CanaryService;
import com.github.soyoger.modelgateway.domain.service.RateLimitService;
import com.github.soyoger.modelgateway.domain.service.RouteService;
import com.github.soyoger.modelgateway.domain.service.StainService;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关自动配置。
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
@ComponentScan({"com.github.soyoger.modelgateway.infrastructure", "com.github.soyoger.modelgateway.adapter"})
@EnableScheduling
public class GatewayAutoConfiguration {

    @Bean
    public Tracer tracer() {
        Resource resource = Resource.getDefault().toBuilder()
            .put("service.name", "model-gateway")
            .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .setResource(resource)
            .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
        return openTelemetry.getTracer("model-gateway");
    }

    @Bean
    public AuthService authService(ApiKeyRepository apiKeyRepository) {
        return new AuthService(apiKeyRepository);
    }

    @Bean
    public RouteService routeService(EndpointRepository endpointRepository) {
        return new RouteService(endpointRepository);
    }

    @Bean
    public CanaryService canaryService(RouteConfigRepository routeConfigRepository) {
        return new CanaryService(routeConfigRepository);
    }

    @Bean
    public StainService stainService(RouteConfigRepository routeConfigRepository) {
        return new StainService(routeConfigRepository);
    }

    @Bean
    public GatewayService gatewayService(AuthService authService,
                                         RateLimitService rateLimitService,
                                         StainService stainService,
                                         CanaryService canaryService,
                                         RouteService routeService) {
        return new GatewayService(authService, rateLimitService, stainService, canaryService, routeService);
    }

    @Bean
    public AdminQueryService adminQueryService(EndpointRepository endpointRepository,
                                               RouteConfigRepository routeConfigRepository) {
        return new AdminQueryService(endpointRepository, routeConfigRepository);
    }
}
