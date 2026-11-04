package com.github.soyoger.modelgateway.infrastructure.client;

import com.github.soyoger.modelgateway.infrastructure.config.GatewayProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClient 工厂，统一超时配置。
 */
@Component
@RequiredArgsConstructor
public class WebClientFactory {

    private final GatewayProperties properties;

    public WebClient create() {
        GatewayProperties.ForwardConfig forward = properties.getForward();
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) forward.getConnectTimeout().toMillis())
            .responseTimeout(forward.getReadTimeout())
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(forward.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(forward.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize((int) forward.getMaxResponseSize()))
            .build();
    }
}
