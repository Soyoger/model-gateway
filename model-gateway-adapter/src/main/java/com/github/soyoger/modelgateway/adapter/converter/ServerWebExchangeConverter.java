package com.github.soyoger.modelgateway.adapter.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.soyoger.modelgateway.client.constant.HeaderConstants;
import com.github.soyoger.modelgateway.client.dto.ChatCompletionRequest;
import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import com.github.soyoger.modelgateway.domain.enums.ProtocolType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * ServerWebExchange 转 GatewayRequest。
 */
@Component
@RequiredArgsConstructor
public class ServerWebExchangeConverter {

    private final ObjectMapper objectMapper;

    public Mono<GatewayRequest> toGatewayRequest(ServerWebExchange exchange, ProtocolType protocol, String fallbackModel) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = exchange.getAttributeOrDefault(HeaderConstants.X_REQUEST_ID, "");

        Map<String, String> headers = request.getHeaders().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
        Map<String, String> queryParams = request.getQueryParams().toSingleValueMap();

        return request.getBody()
            .collectList()
            .map(dataBuffers -> {
                byte[] bytes = new byte[dataBuffers.stream().mapToInt(b -> b.readableByteCount()).sum()];
                int offset = 0;
                for (var buffer : dataBuffers) {
                    int len = buffer.readableByteCount();
                    buffer.read(bytes, offset, len);
                    offset += len;
                }
                return bytes.length > 0 ? new String(bytes) : "";
            })
            .defaultIfEmpty("")
            .map(body -> {
                String model = resolveModel(headers, body, fallbackModel);
                return GatewayRequest.builder()
                    .requestId(requestId)
                    .protocol(protocol)
                    .method(request.getMethod().name())
                    .path(request.getPath().value())
                    .model(model)
                    .headers(headers)
                    .queryParams(queryParams)
                    .body(body)
                    .build();
            });
    }

    public GatewayRequest toWebSocketRequest(ServerWebExchange exchange, String model) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = exchange.getAttributeOrDefault(HeaderConstants.X_REQUEST_ID, "");
        Map<String, String> headers = request.getHeaders().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
        return GatewayRequest.builder()
            .requestId(requestId)
            .protocol(ProtocolType.WEBSOCKET)
            .method("WS")
            .path(request.getPath().value())
            .model(model)
            .headers(headers)
            .queryParams(request.getQueryParams().toSingleValueMap())
            .body("")
            .build();
    }

    private String resolveModel(Map<String, String> headers, String body, String fallbackModel) {
        String headerModel = headers.get(HeaderConstants.X_MODEL);
        if (headerModel != null && !headerModel.isBlank()) {
            return headerModel;
        }
        if (body != null && !body.isBlank()) {
            try {
                ChatCompletionRequest req = objectMapper.readValue(body, ChatCompletionRequest.class);
                if (req.getModel() != null && !req.getModel().isBlank()) {
                    return req.getModel();
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackModel;
    }
}
