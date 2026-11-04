package com.github.soyoger.modelgateway.application.service;

import com.github.soyoger.modelgateway.application.command.ForwardCommand;
import com.github.soyoger.modelgateway.application.command.ForwardResult;
import com.github.soyoger.modelgateway.application.command.WebSocketMessage;
import com.github.soyoger.modelgateway.domain.aggregate.RouteResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 协议转发应用服务接口。
 */
public interface ForwardService {

    /**
     * 转发 HTTP 同步请求。
     */
    Mono<ForwardResult<byte[]>> forwardHttp(ForwardCommand command, RouteResult route);

    /**
     * 转发 HTTP-SSE 流式请求。
     */
    Mono<ForwardResult<Flux<String>>> forwardSse(ForwardCommand command, RouteResult route);

    /**
     * 转发 WebSocket 请求（双端代理）。
     *
     * @param command  转发命令
     * @param route    路由结果
     * @param inbound  来自客户端的消息流
     * @param callback 回调接口，用于将后端返回的消息发送回客户端
     */
    Mono<Void> forwardWebSocket(ForwardCommand command, RouteResult route,
                                Flux<WebSocketMessage> inbound,
                                WebSocketProxyCallback callback);

    /**
     * WebSocket 代理回调。
     */
    interface WebSocketProxyCallback {
        Mono<Void> send(Flux<WebSocketMessage> outbound);
    }
}
