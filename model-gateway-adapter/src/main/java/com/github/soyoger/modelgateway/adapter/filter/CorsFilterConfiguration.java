package com.github.soyoger.modelgateway.adapter.filter;

import com.github.soyoger.modelgateway.domain.policy.CorsPolicy;
import com.github.soyoger.modelgateway.domain.repository.RouteConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Optional;

/**
 * CORS 过滤器配置。
 */
@Configuration
@RequiredArgsConstructor
public class CorsFilterConfiguration {

    private final RouteConfigRepository routeConfigRepository;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        CorsPolicy policy = routeConfigRepository.loadCorsPolicy();
        if (policy != null) {
            Optional.ofNullable(policy.getAllowedOrigins()).ifPresent(origins -> origins.forEach(config::addAllowedOriginPattern));
            Optional.ofNullable(policy.getAllowedMethods()).ifPresent(methods -> methods.forEach(config::addAllowedMethod));
            Optional.ofNullable(policy.getAllowedHeaders()).ifPresent(headers -> headers.forEach(config::addAllowedHeader));
            Optional.ofNullable(policy.getExposedHeaders()).ifPresent(headers -> headers.forEach(config::addExposedHeader));
            config.setAllowCredentials(policy.isAllowCredentials());
            config.setMaxAge(policy.getMaxAge());
        } else {
            config.addAllowedOriginPattern("*");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
