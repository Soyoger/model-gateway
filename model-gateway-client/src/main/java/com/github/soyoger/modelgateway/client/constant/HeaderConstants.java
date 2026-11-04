package com.github.soyoger.modelgateway.client.constant;

/**
 * 网关统一 Header 常量。
 */
public final class HeaderConstants {

    private HeaderConstants() {
    }

    public static final String X_REQUEST_ID = "X-Request-Id";
    public static final String X_TRACE_ID = "X-Trace-Id";
    public static final String X_MODEL = "X-Model";
    public static final String X_CANARY = "X-Canary";
    public static final String X_STAIN = "X-Stain";
    public static final String X_STAIN_TRACE = "X-Stain-Trace";
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_API_KEY = "X-API-Key";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
}
