package com.github.soyoger.modelgateway.adapter.exception;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.client.constant.HeaderConstants;
import com.github.soyoger.modelgateway.client.dto.ErrorResponse;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorResponse> handleGatewayException(GatewayException ex, ServerWebExchange exchange) {
        String requestId = resolveRequestId(exchange, ex.getRequestId());
        String traceId = exchange.getAttributeOrDefault(HeaderConstants.X_TRACE_ID, "");
        log.warn("Gateway exception: code={}, message={}, requestId={}", ex.getErrorCode().getCode(), ex.getMessage(), requestId);

        ErrorResponse response = new ErrorResponse(ex.getErrorCode().getCode(), ex.getMessage(), requestId, traceId);
        HttpStatus status = mapStatus(ex.getErrorCode());
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, ServerWebExchange exchange) {
        String requestId = resolveRequestId(exchange, null);
        String traceId = exchange.getAttributeOrDefault(HeaderConstants.X_TRACE_ID, "");
        log.error("Internal error, requestId={}", requestId, ex);

        ErrorResponse response = new ErrorResponse(GatewayErrorCode.INTERNAL_ERROR.getCode(),
            GatewayErrorCode.INTERNAL_ERROR.getMessage(), requestId, traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response);
    }

    private String resolveRequestId(ServerWebExchange exchange, String fallback) {
        String requestId = exchange.getAttribute(HeaderConstants.X_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private HttpStatus mapStatus(GatewayErrorCode code) {
        return switch (code) {
            case INVALID_API_KEY, UNAUTHORIZED_MODEL -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case MODEL_NOT_FOUND, ROUTE_FAILED, NO_AVAILABLE_ENDPOINT -> HttpStatus.NOT_FOUND;
            case REQUEST_SIZE_EXCEEDED, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORWARD_ERROR -> HttpStatus.BAD_GATEWAY;
            case PROTOCOL_NOT_SUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
