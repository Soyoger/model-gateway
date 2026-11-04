package com.github.soyoger.modelgateway.client.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 网关统一错误码。
 */
@Getter
@RequiredArgsConstructor
public enum GatewayErrorCode {

    SUCCESS("GW-0000", "成功"),
    INVALID_API_KEY("GW-0001", "API Key 无效或已禁用"),
    RATE_LIMITED("GW-0002", "请求触发限流"),
    MODEL_NOT_FOUND("GW-0003", "模型不存在"),
    NO_AVAILABLE_ENDPOINT("GW-0004", "无可用后端节点"),
    ROUTE_FAILED("GW-0005", "路由失败"),
    FORWARD_ERROR("GW-0006", "后端转发异常"),
    PROTOCOL_NOT_SUPPORTED("GW-0007", "协议不支持"),
    REQUEST_SIZE_EXCEEDED("GW-0008", "请求体大小超过限制"),
    UNAUTHORIZED_MODEL("GW-0009", "当前 Key 无权访问该模型"),
    INVALID_REQUEST("GW-0010", "请求参数非法"),
    INTERNAL_ERROR("GW-9999", "网关内部错误");

    private final String code;
    private final String message;
}
