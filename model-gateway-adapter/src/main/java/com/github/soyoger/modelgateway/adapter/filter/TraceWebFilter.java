package com.github.soyoger.modelgateway.adapter.filter;

import com.github.soyoger.modelgateway.client.constant.HeaderConstants;
import com.github.soyoger.modelgateway.infrastructure.trace.OpenTelemetryTracer;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Trace / RequestId 过滤器，位于最外层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceWebFilter implements WebFilter, Ordered {

    private final OpenTelemetryTracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        final String rid = requestId;

        Span span = tracer.startSpan("gateway-request", rid);
        String traceId = tracer.getTraceId(span);

        exchange.getAttributes().put(HeaderConstants.X_REQUEST_ID, rid);
        exchange.getAttributes().put(HeaderConstants.X_TRACE_ID, traceId);

        exchange.getResponse().getHeaders().set(HeaderConstants.X_REQUEST_ID, rid);
        exchange.getResponse().getHeaders().set(HeaderConstants.X_TRACE_ID, traceId);

        MDC.put("request_id", rid);
        MDC.put("trace_id", traceId);

        return chain.filter(exchange)
            .contextWrite(Context.of(HeaderConstants.X_REQUEST_ID, rid, Span.class, span))
            .doFinally(signal -> {
                MDC.remove("request_id");
                MDC.remove("trace_id");
                tracer.end(span);
            });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
