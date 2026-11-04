package com.github.soyoger.modelgateway.adapter.websocket;

import com.github.soyoger.modelgateway.application.command.ForwardCommand;
import com.github.soyoger.modelgateway.application.command.WebSocketMessage;
import com.github.soyoger.modelgateway.application.service.ForwardService;
import com.github.soyoger.modelgateway.application.service.GatewayService;
import com.github.soyoger.modelgateway.client.constant.HeaderConstants;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.enums.ProtocolType;
import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WebSocket 双端代理处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelGatewayWebSocketHandler implements WebSocketHandler {

    private final GatewayService gatewayService;
    private final ForwardService forwardService;
    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        GatewayProperties.WebSocketConfig wsConfig = gatewayProperties.getWebsocket();
        boolean heartbeatEnabled = wsConfig.isHeartbeatEnabled();
        Duration heartbeatInterval = wsConfig.getHeartbeatInterval();
        Duration idleTimeout = wsConfig.getIdleTimeout();

        HandshakeInfo handshakeInfo = session.getHandshakeInfo();
        String model = handshakeInfo.getUri().getQuery();
        if (model != null && model.startsWith("model=")) {
            model = model.substring(6);
        }
        final String finalModel = model != null ? model : "default";

        GatewayRequest request = buildRequest(handshakeInfo, finalModel);
        String requestId = request.getRequestId();

        // 协议层心跳：定时向客户端发送 PING 帧
        Flux<org.springframework.web.reactive.socket.WebSocketMessage> clientHeartbeat = heartbeatEnabled
            ? Flux.interval(heartbeatInterval)
                .map(i -> session.pingMessage(factory -> factory.allocateBuffer(0)))
                .doOnNext(m -> log.debug("Send client ping, requestId={}", requestId))
            : Flux.empty();

        Flux<WebSocketMessage> inbound = session.receive()
            .timeout(idleTimeout, Mono.error(new RuntimeException("client idle timeout, requestId=" + requestId)))
            .filter(msg -> {
                org.springframework.web.reactive.socket.WebSocketMessage.Type type = msg.getType();
                if (type == org.springframework.web.reactive.socket.WebSocketMessage.Type.PING
                    || type == org.springframework.web.reactive.socket.WebSocketMessage.Type.PONG) {
                    log.debug("Client {} received, requestId={}", type, requestId);
                    return false;
                }
                return true;
            })
            .map(msg -> {
                byte[] payload = new byte[msg.getPayload().readableByteCount()];
                msg.getPayload().read(payload);
                return new WebSocketMessage(
                    msg.getType() == org.springframework.web.reactive.socket.WebSocketMessage.Type.TEXT ? WebSocketMessage.Type.TEXT : WebSocketMessage.Type.BINARY,
                    payload);
            });

        ForwardService.WebSocketProxyCallback callback = outbound -> {
            Flux<org.springframework.web.reactive.socket.WebSocketMessage> springOutbound = outbound
                .filter(msg -> msg.getType() != WebSocketMessage.Type.CLOSE)
                .map(msg -> session.binaryMessage(factory -> factory.wrap(ByteBuffer.wrap(msg.getPayload()))));
            return session.send(Flux.merge(springOutbound, clientHeartbeat));
        };

        return gatewayService.process(request)
            .flatMap(route -> {
                ForwardCommand command = new ForwardCommand(request);
                return forwardService.forwardWebSocket(command, route, inbound, callback);
            })
            .doOnError(e -> log.error("WebSocket proxy error, requestId={}", requestId, e))
            .onErrorResume(e -> session.close());
    }

    private GatewayRequest buildRequest(HandshakeInfo handshakeInfo, String model) {
        Map<String, String> headers = handshakeInfo.getHeaders().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
        String requestId = headers.getOrDefault(HeaderConstants.X_REQUEST_ID, UUID.randomUUID().toString().replace("-", ""));
        return GatewayRequest.builder()
            .requestId(requestId)
            .protocol(ProtocolType.WEBSOCKET)
            .method("WS")
            .path(handshakeInfo.getUri().getPath())
            .model(model)
            .headers(headers)
            .body("")
            .build();
    }
}
