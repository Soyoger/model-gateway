package com.github.soyoger.modelgateway.infrastructure.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenTelemetry Trace 工具。
 */
@Component
@RequiredArgsConstructor
public class OpenTelemetryTracer {

    private final Tracer tracer;

    public Span startSpan(String name, String requestId) {
        return tracer.spanBuilder(name)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("request.id", requestId != null ? requestId : "")
            .startSpan();
    }

    public Scope makeCurrent(Span span) {
        return span.makeCurrent();
    }

    public String getTraceId(Span span) {
        return span.getSpanContext().getTraceId();
    }

    public void end(Span span) {
        if (span != null) {
            span.end();
        }
    }

    public void inject(HttpHeaders headers) {
        TextMapSetter<HttpHeaders> setter = HttpHeaders::set;
        io.opentelemetry.api.GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), headers, setter);
    }

    public void inject(Map<String, String> map) {
        TextMapSetter<Map<String, String>> setter = Map::put;
        io.opentelemetry.api.GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), map, setter);
    }
}
