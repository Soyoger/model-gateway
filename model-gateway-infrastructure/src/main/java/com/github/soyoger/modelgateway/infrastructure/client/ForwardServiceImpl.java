package com.github.soyoger.modelgateway.infrastructure.client;

import com.github.soyoger.modelgateway.application.command.ForwardCommand;
import com.github.soyoger.modelgateway.application.command.ForwardResult;
import com.github.soyoger.modelgateway.application.command.WebSocketMessage;
import com.github.soyoger.modelgateway.application.service.ForwardService;
import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import com.github.soyoger.modelgateway.infrastructure.trace.OpenTelemetryTracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;

/**
 * 协议转发服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForwardServiceImpl implements ForwardService {

    private final WebClientFactory webClientFactory;
    private final OpenTelemetryTracer tracer;
    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<ForwardResult<byte[]>> forwardHttp(ForwardCommand command, RouteResult route) {
        GatewayRequest request = command.getRequest();
        URI targetUri = buildTargetUri(route.getEndpoint(), request.getPath());
        HttpHeaders headers = buildHeaders(request);

        WebClient webClient = webClientFactory.create();
        return webClient.method(HttpMethod.valueOf(request.getMethod()))
            .uri(targetUri)
            .headers(h -> h.addAll(headers))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request.getBody() != null ? request.getBody() : "")
            .exchangeToMono(response ->
                response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(body -> ForwardResult.<byte[]>builder()
                        .statusCode(response.statusCode().value())
                        .headers(response.headers().asHttpHeaders())
                        .body(body)
                        .streaming(false)
                        .build())
            )
            .onErrorMap(e -> new GatewayException(GatewayErrorCode.FORWARD_ERROR,
                "转发失败: " + e.getMessage(), request.getRequestId(), e));
    }

    @Override
    public Mono<ForwardResult<Flux<String>>> forwardSse(ForwardCommand command, RouteResult route) {
        GatewayRequest request = command.getRequest();
        URI targetUri = buildTargetUri(route.getEndpoint(), request.getPath());
        HttpHeaders headers = buildHeaders(request);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

        WebClient webClient = webClientFactory.create();
        Flux<String> flux = webClient.method(HttpMethod.valueOf(request.getMethod()))
            .uri(targetUri)
            .headers(h -> h.addAll(headers))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request.getBody() != null ? request.getBody() : "")
            .retrieve()
            .bodyToFlux(String.class)
            .onErrorMap(e -> new GatewayException(GatewayErrorCode.FORWARD_ERROR,
                "SSE 转发失败: " + e.getMessage(), request.getRequestId(), e));

        return Mono.just(ForwardResult.<Flux<String>>builder()
            .statusCode(200)
            .headers(new HttpHeaders())
            .body(flux)
            .streaming(true)
            .build());
    }

    @Override
    public Mono<Void> forwardWebSocket(ForwardCommand command, RouteResult route,
                                       Flux<WebSocketMessage> inbound,
                                       WebSocketProxyCallback callback) {
        GatewayRequest request = command.getRequest();
        URI targetUri = buildWsTargetUri(route.getEndpoint(), request.getPath());
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        HttpHeaders headers = buildHeaders(request);

        GatewayProperties.WebSocketConfig wsConfig = gatewayProperties.getWebsocket();
        boolean backendHeartbeat = wsConfig.isBackendHeartbeatEnabled();
        Duration heartbeatInterval = wsConfig.getHeartbeatInterval();
        Duration idleTimeout = wsConfig.getIdleTimeout();
        String requestId = request.getRequestId();

        return client.execute(targetUri, headers, backendSession -> {
            // 协议层心跳：定时向后端发送 PING 帧
            Flux<org.springframework.web.reactive.socket.WebSocketMessage> backendHeartbeatFlux = backendHeartbeat
                ? Flux.interval(heartbeatInterval)
                    .map(i -> backendSession.pingMessage(factory -> factory.allocateBuffer(0)))
                    .doOnNext(m -> log.debug("Send backend ping, requestId={}", requestId))
                : Flux.empty();

            Flux<WebSocketMessage> backendInbound = backendSession.receive()
                .timeout(idleTimeout, Mono.error(new RuntimeException("backend idle timeout, requestId=" + requestId)))
                .filter(msg -> {
                    org.springframework.web.reactive.socket.WebSocketMessage.Type type = msg.getType();
                    if (type == org.springframework.web.reactive.socket.WebSocketMessage.Type.PING
                        || type == org.springframework.web.reactive.socket.WebSocketMessage.Type.PONG) {
                        log.debug("Backend {} received, requestId={}", type, requestId);
                        return false;
                    }
                    return true;
                })
                .map(msg -> new WebSocketMessage(
                    msg.getType() == org.springframework.web.reactive.socket.WebSocketMessage.Type.TEXT ? WebSocketMessage.Type.TEXT : WebSocketMessage.Type.BINARY,
                    payloadToBytes(msg.getPayload())));

            Flux<org.springframework.web.reactive.socket.WebSocketMessage> clientToBackend = inbound
                .filter(msg -> msg.getType() != WebSocketMessage.Type.CLOSE)
                .map(msg -> backendSession.binaryMessage(factory -> factory.wrap(ByteBuffer.wrap(msg.getPayload()))));

            Mono<Void> sendToBackend = backendSession.send(Flux.merge(clientToBackend, backendHeartbeatFlux));
            Mono<Void> sendToClient = callback.send(backendInbound);

            return Mono.zip(sendToBackend, sendToClient).then();
        });
    }

    private URI buildTargetUri(ModelEndpoint endpoint, String path) {
        String base = endpoint.getBaseUrl().toString();
        if (path == null || path.isEmpty()) {
            return URI.create(base);
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return URI.create(base + path.substring(1));
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return URI.create(base + "/" + path);
        }
        return URI.create(base + path);
    }

    private URI buildWsTargetUri(ModelEndpoint endpoint, String path) {
        URI uri = buildTargetUri(endpoint, path);
        String scheme = "https".equals(uri.getScheme()) ? "wss" : "ws";
        try {
            return new URI(scheme, uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WebSocket URI", e);
        }
    }

    private HttpHeaders buildHeaders(GatewayRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::set);
        }
        // 透传染色体
        if (request.getStains() != null && !request.getStains().isEmpty()) {
            request.getStains().keySet().forEach(stain -> headers.set("X-Stain", stain));
        }
        // 注入 Trace
        tracer.inject(headers);
        return headers;
    }

    private byte[] payloadToBytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }
}
