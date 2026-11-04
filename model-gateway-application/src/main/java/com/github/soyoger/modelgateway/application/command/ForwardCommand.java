package com.github.soyoger.modelgateway.application.command;

import com.github.soyoger.modelgateway.domain.aggregate.GatewayRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 转发命令。
 */
@Data
@AllArgsConstructor
public class ForwardCommand {

    private GatewayRequest request;
}
