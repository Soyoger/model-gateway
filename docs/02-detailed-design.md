# 大模型网关详细设计

## 1. 请求处理主流程

```text
┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  HTTP/WS    │───▶│  Adapter Layer   │───▶│ 统一 Gateway    │
│  /SSE 请求   │    │  协议适配/参数解析  │    │  Request 对象   │
└─────────────┘    └──────────────────┘    └────────┬────────┘
                                                    │
                       ┌────────────────────────────┘
                       ▼
        ┌──────────────────────────────┐
        │   Application: GatewayService │
        │  1. 认证（API Key）            │
        │  2. 限流（Token/Request）      │
        │  3. 模型解析                  │
        │  4. 染色提取与染色判定         │
        │  5. 灰度判定                  │
        │  6. 路由选择                  │
        │  7. 协议转发                  │
        └──────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   ┌─────────┐   ┌──────────┐   ┌──────────┐
   │  HTTP   │   │   SSE    │   │WebSocket │
   │ Forward │   │ Forward  │   │ Forward  │
   └─────────┘   └──────────┘   └──────────┘
```

## 2. 核心领域模型

### 2.1 GatewayRequest（统一请求对象）

```java
public class GatewayRequest {
    private String requestId;      // 全局唯一请求 ID
    private ProtocolType protocol; // HTTP / SSE / WEBSOCKET
    private String model;          // 目标模型，如 gpt-4、qwen-max
    private String path;           // 原始路径
    private HttpHeaders headers;   // 原始 Header
    private String body;           // 请求体（JSON）
    private Map<String, String> stains; // 染色体标记
    private ApiKeyCredential credential; // 认证信息
}
```

### 2.2 ModelEndpoint（模型节点）

```java
public class ModelEndpoint {
    private String id;
    private String model;      // 所属模型
    private String version;    // 版本，如 v1/v2
    private URI baseUrl;       // 后端地址
    private Map<String, String> tags; // 标签，如 region=cn，gpu=a100
    private int weight;        // 权重
    private boolean canary;    // 是否灰度节点
    private Set<String> stains; // 支持的染色体标记
    private EndpointStatus status;
}
```

### 2.3 RouteResult（路由结果）

```java
public class RouteResult {
    private ModelEndpoint endpoint;
    private RouteReason reason;     // WEIGHT / TAG / CANARY / STAIN / VERSION
    private boolean canaryHit;      // 是否命中灰度
    private String stainHit;        // 命中的染色体标记
}
```

## 3. 路由策略设计

### 3.1 路由策略接口

```java
public interface RouteStrategy {
    RouteResult route(GatewayRequest request, List<ModelEndpoint> candidates);
}
```

### 3.2 策略实现

| 策略 | 说明 |
|------|------|
| `WeightRouteStrategy` | 按权重随机选择 |
| `TagRouteStrategy` | 按请求标签匹配 |
| `VersionRouteStrategy` | 按版本号优先 |
| `StainRouteStrategy` | 染色流量优先命中染色节点 |
| `CompositeRouteStrategy` | 组合策略：染色 > 灰度 > 标签 > 权重 |

### 3.3 组合策略优先级

```text
1. 若请求携带染色标记 → 优先选择支持该染色的节点
2. 若命中灰度规则 → 在灰度节点池中选择
3. 若请求指定版本/标签 → 按版本/标签过滤
4. 默认按权重随机选择基线节点
```

## 4. 灰度规则设计

### 4.1 灰度规则模型

```java
public class CanaryRule {
    private String ruleId;
    private String model;          // 生效模型
    private int percentage;        // 百分比 0-100
    private List<String> userWhitelist;
    private Map<String, String> headerMatch;
    private Map<String, String> cookieMatch;
    private String targetVersion;  // 灰度目标版本
}
```

### 4.2 判定流程

```text
1. 按 model 找到对应灰度规则
2. 先匹配白名单（命中则直接灰度）
3. 再匹配 Header/Cookie 条件
4. 最后按 requestId hash 取模与百分比比较
5. 命中则返回 targetVersion，用于后续路由过滤
```

## 5. 染色体设计

### 5.1 染色规则

```java
public class StainRule {
    private String stainKey;       // 染色标记，如 experiment-a
    private String model;
    private int samplingRate;      // 抽样百分比
    private Map<String, String> headerMatch; // 命中 Header 即染色
    private boolean forceRoute;    // 是否强制路由到染色节点
}
```

### 5.2 染色处理流程

```text
1. 从请求 Header/Cookie 提取已有染色标记
2. 若未命中，按规则抽样并附加染色
3. 将 stain 写入 GatewayRequest 上下文
4. 转发时通过 Header 透传给后端（X-Stain）
5. 响应时通过 Header 回传（X-Stain-Trace）
6. 日志与监控按染色维度拆分
```

## 6. 接入层设计

### 6.1 认证

- API Key 从 Header `Authorization: Bearer {apiKey}` 或 `X-API-Key` 提取。
- `ApiKeyCredential` 校验 Key 合法性、所属租户、模型权限。

### 6.2 限流

- 维度：API Key / 模型 / 全局。
- 算法：令牌桶（Token Bucket）。
- 实现：基于 `Resilience4j RateLimiter`，按 `apiKey:model` 维度缓存限流器实例；后续可替换为 Redis 分布式限流。

### 6.3 IP 黑白名单

- 通过 `IpFilter` 在 Adapter 层前置拦截。

## 7. 协议转发设计

### 7.1 HTTP 转发

```java
public Mono<ResponseEntity<byte[]>> forwardHttp(GatewayRequest request, RouteResult route);
```

- 使用 `WebClient` 构建下游请求。
- 透传 Method、Header、Body。
- 返回下游响应码与 Body。

### 7.2 SSE 转发

```java
public Flux<ServerSentEvent<String>> forwardSse(GatewayRequest request, RouteResult route);
```

- 设置 `Accept: text/event-stream`。
- 使用 `WebClient` 获取 `Flux<String>` 行流。
- 包装为 `ServerSentEvent` 返回给客户端。

### 7.3 WebSocket 转发

```java
public Mono<Void> forwardWebSocket(WebSocketSession clientSession, GatewayRequest request);
```

- 方案 A：Gateway 作为 WS 代理，同时维护与客户端、后端两个 WS 会话，双向转发消息。
- 方案 B：Gateway 接收 WS 消息后，按单条消息转 HTTP/SSE 请求，异步回写 WS 响应。

**本次采用方案 A（WS↔WS 双端代理）**，保持全双工长连接语义，适用于后端 LLM 服务本身提供 WebSocket 接口的场景。

## 8. 接口与 Swagger 设计

### 8.1 统一入口

| 协议 | 入口 | 说明 |
|------|------|------|
| HTTP | `POST /v1/chat/completions` | 同步/可流式 |
| SSE | `POST /v1/chat/completions?stream=true` | 强制流式 |
| WebSocket | `ws://host/ws/v1/chat` | 长连接 |

### 8.2 Swagger 暴露

- 使用 SpringDoc 自动生成 OpenAPI 3。
- 访问 `/swagger-ui.html` 查看文档。
- 通过 `@RouterOperation` 或 `@Operation` 注解描述流式接口。

### 8.3 Admin 接口

| 接口 | 说明 |
|------|------|
| `GET /admin/routes` | 查看当前路由表 |
| `POST /admin/routes/refresh` | 刷新路由配置 |
| `GET /admin/canary/rules` | 查看灰度规则 |
| `GET /admin/stain/rules` | 查看染色规则 |

## 9. 错误码设计

| 错误码 | 含义 |
|--------|------|
| `GW-0001` | API Key 无效 |
| `GW-0002` | 请求限流 |
| `GW-0003` | 模型不存在 |
| `GW-0004` | 无可用节点 |
| `GW-0005` | 路由失败 |
| `GW-0006` | 后端转发异常 |
| `GW-0007` | 协议不支持 |

## 10. 配置示例

```yaml
model-gateway:
  auth:
    api-keys:
      - key: mg-demo-key
        tenant: demo
        allowed-models: ["gpt-4", "qwen-max"]
  routes:
    - model: gpt-4
      strategy: composite
      endpoints:
        - id: gpt-4-baseline
          base-url: http://localhost:8001
          version: v1
          weight: 100
        - id: gpt-4-canary
          base-url: http://localhost:8002
          version: v2
          weight: 0
          canary: true
  canary:
    rules:
      - model: gpt-4
        percentage: 10
        target-version: v2
  stain:
    rules:
      - stain-key: experiment-a
        model: gpt-4
        sampling-rate: 5
        force-route: true
  rate-limit:
    enabled: true
    default-qps: 100
    limit-for-period: 100
    limit-refresh-period: 1s
    timeout-duration: 0s
```
