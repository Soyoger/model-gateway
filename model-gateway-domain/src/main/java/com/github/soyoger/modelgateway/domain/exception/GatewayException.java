package com.github.soyoger.modelgateway.domain.exception;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import lombok.Getter;

/**
 * 网关业务异常。
 */
@Getter
public class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;
    private final String requestId;

    public GatewayException(GatewayErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.requestId = null;
    }

    public GatewayException(GatewayErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = null;
    }

    public GatewayException(GatewayErrorCode errorCode, String message, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public GatewayException(GatewayErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.requestId = null;
    }

    public GatewayException(GatewayErrorCode errorCode, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }
}
