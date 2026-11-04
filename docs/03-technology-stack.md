# 技术选型与核心框架

## 1. 选型原则

- **Reactive First**：网关是 IO 密集型场景，高并发、长连接、流式响应，优先选择响应式栈。
- **生态成熟**：优先选择 Spring 生态，降低团队学习与维护成本。
- **可扩展性**：核心接口抽象，基础设施可替换（限流、路由存储、注册中心）。
- **云原生友好**：支持容器化、配置外置、指标暴露。

## 2. 基础框架

| 组件 | 选型 | 版本建议 | 说明 |
|------|------|----------|------|
| JDK | Oracle OpenJDK / Eclipse Temurin | 21 LTS | 虚拟线程、更好的 GC、长期支持 |
| 构建工具 | Maven | 3.9+ | 多模块管理成熟，IDE 支持好 |
| 主框架 | Spring Boot | 3.3.x | 原生支持 WebFlux、Micrometer、SpringDoc |
| Web 层 | Spring WebFlux | 6.x | Reactive，适合高并发流式转发 |
| 客户端 | WebClient（Reactor Netty） | - | 支持 HTTP/1.1、HTTP/2、长连接池、SSE 流式 |
| WebSocket | Spring WebFlux WebSocket | - | 原生支持 `WebSocketHandler` |
| 文档 | SpringDoc OpenAPI | 2.6.x | 自动生成 Swagger UI，支持 WebFlux RouterFunction |

### 2.1 Spring WebFlux vs Spring MVC

| 维度 | Spring WebFlux | Spring MVC |
|------|----------------|------------|
| 线程模型 | EventLoop + 少量线程 | 每请求一线程 |
| 流式响应 | 原生支持 `Flux` | 需借助 `SseEmitter` / `StreamingResponseBody` |
| WebSocket | 原生响应式 | 基于 Servlet |
| 并发成本 | 更低，适合 C10K | 较高 |
| 生态成熟度 | 够用，文档生成完善 | 更成熟 |

**结论**：网关场景选择 WebFlux。

## 3. 核心能力框架

| 能力 | 选型 | 说明 |
|------|------|------|
| 限流 | Resilience4j RateLimiter（本地） | 第一版本地令牌桶；后续通过 `RateLimiter` 接口替换为 Redis 分布式限流 |
| 路由配置 | YAML（本地）+ 热刷新接口 | 第一版静态配置；后续扩展为 Nacos / Consul / Etcd |
| 负载均衡 | 自研 `LoadBalancer`（基于权重 + 健康状态） | 轻量，可替换为 Spring Cloud LoadBalancer |
| 健康检查 | 自研 `HealthChecker` + 定时任务 | 探测后端节点可用性 |
| 熔断降级 | Resilience4j（可选 Phase 2） | 第一版可预留接口，不强制实现 |
| 序列化 | Jackson | 默认 JSON 处理 |
| 日志 | SLF4J + Logback | 结构化日志，TraceId 透传 |
| 指标 | Micrometer + Prometheus（预留） | Phase 2 接入 |
| 链路追踪 | Micrometer Tracing / Brave（预留） | Phase 2 接入 |
| 缓存 | 无 | 第一版路由表、限流器实例均驻留内存；后续按需引入 Caffeine / Redis |

## 4. 协议与数据格式

| 协议/格式 | 选型 | 说明 |
|-----------|------|------|
| 请求/响应格式 | JSON | 与 OpenAI API 兼容 |
| SSE 数据格式 | `text/event-stream` | 标准 SSE，透传 `data:` 行 |
| WebSocket 消息格式 | JSON | 客户端 WS 帧内为 JSON，Gateway 转 HTTP/SSE |
| 认证方式 | Bearer Token / API Key | 兼容 OpenAI 客户端 |

## 5. 外部依赖规划

### 5.1 第一版（MVP）必须依赖

```xml
<!-- Spring Boot WebFlux -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- WebSocket support in WebFlux -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- OpenAPI / Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
    <version>2.6.0</version>
</dependency>

<!-- Local rate limiter -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-ratelimiter</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 5.2 后续可选依赖

| 场景 | 依赖 |
|------|------|
| Redis 分布式限流 | `resilience4j-ratelimiter` + Redis / `lettuce-core` 自定义实现 |
| 注册中心 Nacos | `spring-cloud-starter-alibaba-nacos-discovery` |
| 配置中心 Nacos | `spring-cloud-starter-alibaba-nacos-config` |
| 熔断降级 | `resilience4j-spring-boot3` |
| 链路追踪 | `micrometer-tracing-bridge-brave` |
| 指标上报 | `micrometer-registry-prometheus` |
| 数据库（配额/租户） | `spring-boot-starter-data-r2dbc` + `r2dbc-postgresql` |

## 6. 协议转发技术细节

### 6.1 HTTP 转发

使用 `WebClient` 的 `exchangeToMono` 或 `retrieve`，保留响应状态码与 Header：

```java
webClient.method(method)
    .uri(targetUri)
    .headers(h -> h.addAll(filteredHeaders))
    .bodyValue(body)
    .exchangeToMono(response ->
        response.bodyToMono(byte[].class)
            .map(bodyBytes -> ResponseEntity.status(response.statusCode())
                .headers(response.headers().asHttpHeaders())
                .body(bodyBytes))
    );
```

### 6.2 SSE 转发

```java
webClient.method(method)
    .uri(targetUri)
    .headers(h -> h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM)))
    .bodyValue(body)
    .retrieve()
    .bodyToFlux(String.class)
    .map(line -> ServerSentEvent.builder(line).build());
```

### 6.3 WebSocket 转发

采用 **方案 B（WS 接入 → 单条消息转 HTTP/SSE）**：

```java
public Mono<Void> handle(WebSocketSession session) {
    return session.receive()
        .flatMap(msg -> {
            GatewayRequest request = parseWsMessage(session, msg);
            return forwardService.forward(request)
                .map(response -> session.textMessage(toJson(response)));
        })
        .flatMap(session::send)
        .then();
}
```

该方案实现简单，且兼容大多数 LLM 使用场景（客户端通过 WS 发送请求，服务端以 HTTP/SSE 获取模型响应后回写 WS）。

## 7. 配置管理策略

### 7.1 第一版：YAML + 内存 + 热刷新接口

- 路由表、灰度规则、染色规则、API Key 配置均放在 `application.yml`。
- 启动时加载到内存。
- 提供 `POST /admin/routes/refresh` 接口，重新读取配置（为后续配置中心预留接口）。

### 7.2 后续：配置中心

- 接入 Nacos / Apollo / Etcd，监听配置变更事件。
- 通过 `ConfigurationRepository` 抽象屏蔽存储差异。

## 8. 可观测性规划

### 8.1 日志

- 全链路 TraceId / SpanId 透传。
- 关键节点打印：请求进入、认证结果、路由结果、转发开始、转发结束、异常。
- 使用 MDC（Mapped Diagnostic Context）或 Reactor Context 透传 TraceId。

### 8.2 指标（Phase 2）

| 指标 | 类型 | 标签 |
|------|------|------|
| `gateway_request_total` | Counter | model, endpoint, protocol, status |
| `gateway_request_duration_seconds` | Timer | model, endpoint, protocol |
| `gateway_route_failures_total` | Counter | model, reason |
| `gateway_rate_limited_total` | Counter | apiKey, model |
| `gateway_active_websocket_sessions` | Gauge | - |

### 8.3 链路追踪（Phase 2）

- 接入 Micrometer Tracing + Brave / OpenTelemetry。
- 向下游服务透传 `traceparent` / `b3` Header。

## 9. 安全规划

| 方面 | 第一版 | 后续 |
|------|--------|------|
| 传输安全 | 由反向代理（Nginx / Ingress）提供 TLS | 可选内置 TLS |
| 认证 | API Key Bearer Token | OAuth2 / JWT |
| 鉴权 | 模型白名单 | RBAC 细粒度权限 |
| 限流 | API Key / 模型 / 全局 | 多租户配额 |
| 敏感数据 | 日志脱敏（不打印完整 API Key） | 请求体关键字脱敏 |
| WAF | 不内置 | 可选集成 |

## 10. 部署与运行

| 项 | 建议 |
|----|------|
| 容器化 | Dockerfile + JRE 21 镜像 |
| 端口 | 8080（业务）+ 8081（管理，可选） |
| 启动方式 | `java -jar model-gateway-bootstrap.jar` |
| 配置外置 | `--spring.config.location=/etc/model-gateway/` |
| 健康检查 | Spring Boot Actuator `/actuator/health` |

## 11. 技术风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| WebFlux 背压处理不当导致内存溢出 | 高 | SSE/WebSocket 使用 `Flux` 背压，限制缓冲；大请求体做大小校验 |
| 后端节点故障导致请求堆积 | 高 | 健康检查 + 失败节点剔除 + 超时配置 |
| API Key 泄露 | 高 | 日志脱敏、定期轮换、最小权限模型 |
| 路由配置错误导致流量异常 | 中 | Admin 接口校验、版本化配置、灰度小流量验证 |
| WebSocket 长连接管理复杂 | 中 | 第一版采用 WS↔WS 双端代理，已增加协议层心跳与空闲超时 |
