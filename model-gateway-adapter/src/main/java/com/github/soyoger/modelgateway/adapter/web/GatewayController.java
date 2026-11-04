package com.github.soyoger.modelgateway.adapter.web;

import com.github.soyoger.modelgateway.adapter.converter.ServerWebExchangeConverter;
import com.github.soyoger.modelgateway.application.command.ForwardCommand;
import com.github.soyoger.modelgateway.application.service.ForwardService;
import com.github.soyoger.modelgateway.application.service.GatewayService;
import com.github.soyoger.modelgateway.client.dto.ChatCompletionRequest;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.enums.ProtocolType;
import com.github.soyoger.modelgateway.infrastructure.trace.OpenTelemetryTracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 网关 HTTP / SSE 入口。
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Model Gateway", description = "大模型网关统一入口")
public class GatewayController {

    private final GatewayService gatewayService;
    private final ForwardService forwardService;
    private final ServerWebExchangeConverter converter;
    private final OpenTelemetryTracer tracer;

    @Operation(summary = "Chat Completions", description = "OpenAI 兼容的 Chat Completion 接口，支持同步与 SSE 流式")
    @ApiResponse(responseCode = "200", description = "同步响应")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/event-stream"))
    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> chatCompletions(ServerWebExchange exchange,
                                                   @RequestBody ChatCompletionRequest request) {
        boolean stream = Boolean.TRUE.equals(request.getStream());
        ProtocolType protocol = stream ? ProtocolType.SSE : ProtocolType.HTTP;

        return converter.toGatewayRequest(exchange, protocol, request.getModel())
            .flatMap(req -> gatewayService.process(req).flatMap(route -> doForward(req, route, stream)));
    }

    @Operation(summary = "按模型转发", description = "通过 URL 路径指定模型进行转发")
    @PostMapping(value = "/models/{model}/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> modelCompletions(ServerWebExchange exchange,
                                                    @PathVariable String model,
                                                    @RequestParam(defaultValue = "false") boolean stream) {
        ProtocolType protocol = stream ? ProtocolType.SSE : ProtocolType.HTTP;
        return converter.toGatewayRequest(exchange, protocol, model)
            .flatMap(req -> gatewayService.process(req).flatMap(route -> doForward(req, route, stream)));
    }

    @SuppressWarnings("unchecked")
    private Mono<ResponseEntity<?>> doForward(GatewayRequest request, RouteResult route, boolean stream) {
        ForwardCommand command = new ForwardCommand(request);
        if (stream) {
            return forwardService.forwardSse(command, route)
                .map(result -> ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(Flux.from(result.getBody())
                        .map(line -> ServerSentEvent.builder(line).build())));
        } else {
            return forwardService.forwardHttp(command, route)
                .map(result -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    if (result.getHeaders() != null) {
                        result.getHeaders().forEach(responseHeaders::addAll);
                    }
                    return ResponseEntity.status(result.getStatusCode())
                        .headers(responseHeaders)
                        .body(result.getBody());
                });
        }
    }
}
