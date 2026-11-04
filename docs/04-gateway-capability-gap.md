# 网关能力检查与补缺

## 1. 检查维度

从通用网关视角出发，按 **接入层、路由层、转发层、治理层、可观测、安全、运维** 七个维度检查当前规划是否完整。

## 2. 能力矩阵

| 维度 | 能力项 | 当前规划状态 | 是否缺失 | 建议 |
|------|--------|--------------|----------|------|
| **接入层** | 协议接入（HTTP/SSE/WS） | 已规划 | 否 | P0 实现 |
| | API Key 认证 | 已规划 | 否 | P0 实现 |
| | 请求鉴权（模型白名单） | 已规划 | 否 | P0 实现 |
| | 限流（本地令牌桶） | 已规划 | 否 | P0 实现 |
| | IP 黑白名单 | 已规划 | 否 | P1 实现 |
| | HTTPS/TLS 卸载 | 建议由反向代理承担 | 部分缺失 | 不内置，文档说明由 Nginx/Ingress 处理 |
| | CORS 处理 | 未明确 | 是 | P1 增加，避免浏览器调用被拦截 |
| | 请求体大小限制 | 未明确 | 是 | P1 增加，防止大文件压垮内存 |
| | 基础 WAF（SQLi/XSS 关键字） | 未规划 | 是 | P2 可选，网关不建议做重 WAF |
| **路由层** | 静态路由表 | 已规划 | 否 | P0 实现 |
| | 动态路由/热刷新 | 已规划接口 | 否 | P1 完善 |
| | 健康检查 | 已规划 | 否 | P1 实现 |
| | 负载均衡（权重） | 已规划 | 否 | P0 实现 |
| | 模型版本路由 | 已规划 | 否 | P0 实现 |
| | 标签路由 | 已规划 | 否 | P1 实现 |
| | 注册中心集成 | 未规划 | 是 | P2 接入 Nacos/Consul |
| **转发层** | HTTP 代理 | 已规划 | 否 | P0 实现 |
| | SSE 流式代理 | 已规划 | 否 | P0 实现 |
| | WebSocket 代理 | 已规划方案 B | 否 | P0 实现 |
| | 连接池管理 | WebClient 默认 | 否 | 调优参数 |
| | 超时控制 | 未明确 | 是 | P0 必须增加 connect/read/write 超时 |
| | 重试机制 | 未明确 | 是 | P1 增加，注意流式响应不重试 |
| | 错误响应标准化 | 已规划错误码 | 否 | P0 实现 |
| **治理层** | 灰度发布 | 已规划 | 否 | P1 实现 |
| | 染色体/流量染色 | 已规划 | 否 | P1 实现 |
| | A/B 测试 | 灰度已覆盖 | 否 | 与灰度共用能力 |
| | 熔断降级 | 未规划 | 是 | P2 接入 Resilience4j |
| | 多租户隔离 | 未规划 | 是 | P2 实现 |
| | 配额/计费 | 未规划 | 是 | P2 实现 |
| | 缓存（Embedding 等） | 未规划 | 是 | P2 可选 |
| **可观测** | 结构化日志 | 已规划 | 否 | P0/P1 实现 |
| | TraceId 透传 | 已规划 | 否 | P1 实现 |
| | Prometheus 指标 | 已规划 | 否 | P2 实现 |
| | 链路追踪 | 已规划 | 否 | P2 实现 |
| | 慢请求告警 | 未规划 | 是 | P2 对接告警系统 |
| **安全** | API Key 认证 | 已规划 | 否 | P0 |
| | 日志脱敏 | 已规划 | 否 | P0 |
| | HTTPS | 反向代理负责 | 否 | 文档说明 |
| | 请求签名 | 未规划 | 是 | P2 可选 |
| | 敏感字段过滤 | 未规划 | 是 | P2 可选 |
| **运维** | Admin 管理接口 | 已规划 | 否 | P1 |
| | Actuator 健康检查 | 已规划 | 否 | P0 |
| | 配置外置 | 已规划 | 否 | P0 |
| | 优雅关闭 | 未明确 | 是 | P1 实现，避免 WS 连接被粗暴断开 |
| | 多实例状态共享 | 未规划 | 是 | P2 通过 Redis/配置中心解决 |

## 3. 关键缺失项与补齐方案

### 3.1 超时控制（P0 必须补齐）

网关若无超时控制，后端慢请求会导致连接堆积。

```yaml
model-gateway:
  forward:
    connect-timeout: 5s
    read-timeout: 60s
    write-timeout: 60s
    max-response-size: 16MB
```

WebClient 配置：

```java
HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    .responseTimeout(Duration.ofSeconds(60))
    .doOnConnected(conn -> conn
        .addHandlerLast(new ReadTimeoutHandler(60))
        .addHandlerLast(new WriteTimeoutHandler(60)));
```

### 3.2 重试机制（P1）

- 仅对幂等/安全的请求重试（GET / 幂等 POST）。
- SSE/WebSocket 流式响应不重试。
- 重试次数不超过 2 次，带指数退避。

### 3.3 CORS 支持（P1）

通过 `CorsWebFilter` 统一处理跨域：

```java
CorsConfiguration config = new CorsConfiguration();
config.addAllowedOriginPattern("*");
config.addAllowedMethod("*");
config.addAllowedHeader("*");
```

### 3.4 请求体大小限制（P1）

- WebFlux 默认 `CodecMaxSize` 限制。
- 在 Adapter 层增加 `ServerWebExchange` 的 `maxInMemorySize` 配置。

### 3.5 优雅关闭（P1）

- 注册 `ApplicationListener<ContextClosedEvent>`。
- 停止接收新请求。
- 等待现有 WebSocket 连接在宽限期内完成或关闭。

### 3.6 熔断降级（P2）

- 使用 Resilience4j 的 `CircuitBreaker` 和 `Retry`。
- 当后端节点失败率达到阈值时熔断，路由到降级节点或返回预设响应。

### 3.7 多租户与配额（P2）

- 每个 API Key 关联 tenant。
- 租户级别限流、模型配额、调用次数统计。

### 3.8 注册中心（P2）

- 后端模型服务注册到 Nacos / Consul。
- Gateway 监听服务变更，动态刷新 `ModelEndpoint` 列表。

## 4. 建议补充的模块/组件

基于以上检查，建议在 COLA 骨架中新增以下组件：

### 4.1 Domain 层新增

| 组件 | 职责 |
|------|------|
| `TimeoutPolicy` | 超时策略聚合 |
| `RetryPolicy` | 重试策略聚合 |
| `CorsPolicy` | 跨域策略聚合 |
| `AccessLogPolicy` | 访问日志策略 |

### 4.2 Application 层新增

| 组件 | 职责 |
|------|------|
| `TimeoutService` | 为每个请求绑定超时参数 |
| `RetryService` | 执行重试策略 |
| `AccessLogService` | 记录访问日志与慢调用 |

### 4.3 Infrastructure 层新增

| 组件 | 职责 |
|------|------|
| `WebClientFactory` | 按后端节点创建/复用 WebClient，统一超时配置 |
| `HealthCheckClient` | 主动探测后端节点健康 |
| `CorsFilterConfiguration` | 跨域过滤器配置 |

### 4.4 Adapter 层新增

| 组件 | 职责 |
|------|------|
| `GlobalExceptionHandler` | 统一异常处理，返回标准错误响应 |
| `CorsWebFilter` | 跨域处理 |
| `RequestSizeLimitFilter` | 请求体大小限制 |
| `GracefulShutdownListener` | 优雅关闭监听器 |

## 5. 模块演进建议

### P0（本次编码必须）

- COLA 多模块骨架
- HTTP / SSE / WebSocket 转发
- 内存路由表 + 权重策略
- API Key 认证
- 本地限流
- Swagger 文档
- **超时控制**
- **统一异常处理**

### P1（快速补齐）

- 健康检查与节点剔除
- 灰度规则 + 染色体规则
- 重试机制
- CORS
- 请求体大小限制
- IP 黑白名单
- Admin 管理接口
- TraceId 透传
- 优雅关闭

### P2（生产化）

- Redis 分布式限流
- 注册中心集成
- 熔断降级
- Prometheus 指标
- 链路追踪
- 多租户/配额/计费
- 缓存
- 配置中心

## 6. 对原始规划的补充结论

原规划已经覆盖网关 **80% 的核心能力**。需要重点补齐的模块是：

1. **超时控制**：必须加入 P0，否则无法在生产环境运行。
2. **统一异常处理**：必须加入 P0，保证错误响应标准化。
3. **健康检查**：P1 加入，保障路由到可用节点。
4. **重试/CORS/请求体限制**：P1 加入，提升健壮性。
5. **熔断/注册中心/多租户**：P2 加入，作为生产化演进。

建议把这些补充点纳入设计文档，并在 COLA 骨架中为它们预留扩展接口。
