package com.github.soyoger.modelgateway.adapter.filter;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求体大小限制过滤器。
 */
@Component
@RequiredArgsConstructor
public class RequestSizeLimitFilter implements WebFilter, Ordered {

    private final GatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        long contentLength = headers.getContentLength();
        long maxSize = properties.getForward().getMaxResponseSize();
        if (contentLength > maxSize) {
            return Mono.error(new GatewayException(GatewayErrorCode.REQUEST_SIZE_EXCEEDED,
                "Request body size " + contentLength + " exceeds limit " + maxSize));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
