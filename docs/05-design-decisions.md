# 设计决策记录

## 1. 用户确认结论

以下决策已经用户确认，作为后续编码依据。

| 序号 | 问题 | 决策 |
|------|------|------|
| 1 | 模块组织 | **Maven 多模块** COLA 分层 |
| 2 | 后端协议 | **OpenAI 兼容协议**（`/v1/chat/completions` 等） |
| 3 | 路由配置 | **YAML 静态配置**，热刷新接口预留 |
| 4 | 限流 | 第一版 **本地内存限流**（Resilience4j RateLimiter） |
| 5 | WebSocket | **WS↔WS 双端代理**，Gateway 同时维护客户端与后端两条 WebSocket 会话 |
| 6 | 灰度 + 染色体 | **本次一并实现**，不是仅预留接口 |
| 7 | 文档 | 必须具备 **Swagger 接口文档** |
| 8 | 补齐范围 | 补齐 **P0 + P1**，并加入 **OpenTelemetry Trace 可观测** |

## 2. 本次编码范围

### 2.1 P0 必须实现

- COLA 多模块骨架
- HTTP / SSE / WebSocket（WS↔WS）转发
- 内存路由表 + 权重/标签/版本路由策略
- API Key 认证 + 模型白名单鉴权
- 本地令牌桶限流
- 超时控制（connect / read / write）
- 统一异常处理 + 标准化错误码
- Swagger 接口文档

### 2.2 P1 本次补齐

- 后端节点健康检查与失效剔除
- 灰度规则引擎（百分比 / Header / Cookie / 用户白名单）
- 染色体规则引擎（Header / 抽样 / 强制路由）
- 幂等请求重试机制（SSE/WS 不重试）
- CORS 跨域支持
- 请求体大小限制
- 优雅关闭
- Admin 管理接口（查看路由表、刷新配置、查看灰度/染色规则）
- OpenTelemetry Trace 全链路追踪

### 2.3 明确不做（P2 后续）

- Redis 分布式限流
- 注册中心集成
- 熔断降级（Resilience4j CircuitBreaker / TimeLimiter，限流已使用 Resilience4j）
- Prometheus 指标
- 多租户 / 配额 / 计费
- 响应缓存
- Admin 可视化控制台

## 3. WebSocket 双端代理方案

### 3.1 方案说明

Gateway 同时维护两条 WebSocket 会话：

```text
Client ──WS──▶ Gateway ──WS──▶ Backend LLM Service
```

- 客户端与 Gateway 建立 WS 连接。
- Gateway 根据模型解析、灰度/染色体判定选择后端节点。
- Gateway 与该后端节点建立第二条 WS 连接。
- 双向转发文本/二进制帧。

### 3.2 适用场景

- 后端 LLM 服务本身提供 WebSocket 接口（如实时语音、持续会话）。
- 需要保持全双工、长连接语义，不能降级为 HTTP/SSE。

### 3.3 实现要点

- 使用 Spring WebFlux `WebSocketHandler` 处理客户端连接。
- 使用 `ReactorNettyWebSocketClient` 主动连接后端。
- 通过 `Flux.merge(clientInbound, backendInbound)` 实现双向转发。
- 任一连接关闭时，关闭另一条连接并清理资源。
- 每条连接关联 TraceId，便于链路追踪。

### 3.4 风险

- 连接数翻倍：Gateway 需要同时维护客户端 + 后端连接。
- 需要处理好背压与异常关闭。
- 后端节点切换时必须先建连再迁移，避免消息丢失。

## 4. OpenTelemetry Trace 可观测

### 4.1 目标

- 请求进入 Gateway 时生成 Trace。
- TraceId 沿 Gateway 内部各层透传。
- 向后端 LLM 服务透传 `traceparent` Header。
- 向客户端响应 Header 中回传 `X-Trace-Id`。

### 4.2 实现方式

- 引入 `opentelemetry-api`、`opentelemetry-extension-annotations`。
- 在 Adapter 层通过 `WebFilter` 创建 Span。
- 使用 Reactor Context 透传 `Context`。
- 在 Infrastructure 层 WebClient 请求前注入 `traceparent`。

### 4.3 与日志结合

- MDC 中设置 `trace_id`、`span_id`。
- 日志输出包含 TraceId，便于与链路系统关联。

## 5. OpenAI 兼容协议

### 5.1 请求格式

```json
{
  "model": "gpt-4",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "stream": false
}
```

### 5.2 响应格式

同步：

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "model": "gpt-4",
  "choices": [...]
}
```

SSE 流式：

```text
data: {"id":"chatcmpl-xxx","choices":[{"delta":{"content":"你"}}]}

data: {"id":"chatcmpl-xxx","choices":[{"delta":{"content":"好"}}]}

data: [DONE]
```

### 5.3 模型解析

- 优先从请求体 JSON 字段 `model` 读取。
- 若不存在，从 Header `X-Model` 读取。
- 若仍不存在，从 URL Path 的占位符读取（如 `/v1/models/{model}/...`）。

## 6. 路由配置结构

```yaml
model-gateway:
  auth:
    api-keys:
      - key: mg-demo-key
        tenant: demo
        allowed-models: ["gpt-4", "qwen-max"]
        rate-limit-qps: 100
  routes:
    - model: gpt-4
      strategy: composite
      endpoints:
        - id: gpt-4-baseline
          base-url: http://localhost:8001
          version: v1
          weight: 90
          tags:
            region: cn
        - id: gpt-4-canary
          base-url: http://localhost:8002
          version: v2
          weight: 10
          canary: true
          tags:
            region: cn
  canary:
    rules:
      - rule-id: gpt-4-v2-canary
        model: gpt-4
        percentage: 10
        target-version: v2
        header-match:
          X-Canary: "true"
        cookie-match:
          canary: "true"
        user-whitelist: ["user-001", "user-002"]
  stain:
    rules:
      - rule-id: experiment-a
        stain-key: experiment-a
        model: gpt-4
        sampling-rate: 5
        header-match:
          X-Stain: "experiment-a"
        force-route: true
        target-endpoint-ids: ["gpt-4-canary"]
  forward:
    connect-timeout: 5s
    read-timeout: 60s
    write-timeout: 60s
    max-response-size: 16MB
    retry-count: 1
  cors:
    allowed-origins: ["*"]
    allowed-methods: ["*"]
    allowed-headers: ["*"]
```

## 7. Admin 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/routes` | 查看当前路由表 |
| POST | `/admin/routes/refresh` | 重新加载 YAML 路由配置 |
| GET | `/admin/canary/rules` | 查看灰度规则 |
| GET | `/admin/stain/rules` | 查看染色规则 |
| GET | `/admin/endpoints/health` | 查看后端节点健康状态 |

## 8. Swagger 暴露点

- Swagger UI：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`

对 HTTP/SSE 入口和 Admin 接口均添加 `@Operation` / `@RouterOperation` 注解，确保文档完整。
