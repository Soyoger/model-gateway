package com.github.soyoger.modelgateway.bootstrap;

import com.github.soyoger.modelgateway.infrastructure.config.GatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 大模型网关启动入口。
 */
@SpringBootApplication
@Import(GatewayAutoConfiguration.class)
public class ModelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelGatewayApplication.class, args);
    }
}
