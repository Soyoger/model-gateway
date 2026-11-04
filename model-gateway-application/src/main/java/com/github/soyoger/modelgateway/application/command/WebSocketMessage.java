package com.github.soyoger.modelgateway.application.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用层 WebSocket 消息抽象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    public enum Type {
        TEXT,
        BINARY,
        CLOSE
    }

    private Type type;
    private byte[] payload;
}
