# 大模型网关 COLA 架构规划

## 1. 项目定位

`model-gateway` 是面向大模型（LLM）服务的统一接入网关，位于客户端与后端多模型服务之间，承担：

- 多协议接入（HTTP / WebSocket / HTTP-SSE）
- 统一路由与流量转发
- 接入层安全（认证、鉴权、限流）
- 灰度发布与 A/B 路由
- 染色体（流量染色与隔离追踪）
- 统一 OpenAPI / Swagger 接口文档暴露

## 2. 架构选型：COLA 4.0

采用 COLA（Clean Object-oriented and Layered Architecture）四层结构：

```text
┌─────────────────────────────────────────────────────────────┐
│                        Client 层                            │
│  SDK / DTO / API 定义 / 错误码 / 常量                         │
├─────────────────────────────────────────────────────────────┤
│                       Adapter 层                             │
│  Controller / WebSocketHandler / SSEHandler / 拦截器          │
├─────────────────────────────────────────────────────────────┤
│                    Application 层                            │
│  编排服务 / 路由服务 / 灰度服务 / 染色体服务 / 转发服务         │
├─────────────────────────────────────────────────────────────┤
│                       Domain 层                              │
│  路由策略、染色体规则、限流规则、模型节点等聚合与领域服务       │
├─────────────────────────────────────────────────────────────┤
│                     Infrastructure 层                        │
│  HTTP/WebFlux 客户端、配置存储、注册中心、缓存、监控埋点        │
└─────────────────────────────────────────────────────────────┘
```

- **Client 层**：对外暴露的 API 契约，包含请求/响应 DTO、错误码、常量。
- **Adapter 层**：协议适配入口，将 HTTP / WebSocket / SSE 请求转换为应用层命令。
- **Application 层**：编排领域服务完成一次完整请求处理，无业务状态，只有用例编排。
- **Domain 层**：核心业务能力（路由、灰度、染色体、限流）的模型与策略。
- **Infrastructure 层**：技术细节实现，如 WebClient、负载均衡、配置中心、指标上报。

## 3. 核心能力矩阵

| 能力 | 说明 | 优先级 |
|------|------|--------|
| 多协议接入 | 同步 HTTP、流式 SSE、全双工 WebSocket | P0 |
| 流量转发 | 反向代理到后端 LLM 服务，支持流式透传 | P0 |
| 智能路由 | 按模型名、版本、标签、权重、区域路由 | P0 |
| 接入层 | API Key 认证、Token 限流、IP 黑白名单 | P0 |
| 灰度发布 | 按流量百分比、用户标识、Header、Cookie 灰度 | P1 |
| 染色体 | 请求染色、染色透传、按染色隔离路由/监控 | P1 |
| Swagger 文档 | 自动生成 OpenAPI 文档，暴露模型接口 | P0 |
| 超时控制 | connect/read/write 超时与最大响应体限制 | P0 |
| 统一异常 | 全局异常处理，标准化错误响应 | P0 |
| 可观测 | TraceId 透传、日志、慢调用指标 | P1 |
| 健康检查 | 后端节点主动探测与失效剔除 | P1 |
| 重试机制 | 幂等请求失败重试（流式响应除外） | P1 |
| CORS | 跨域资源共享配置 | P1 |

## 4. 协议支持设计

### 4.1 HTTP
- 同步请求：客户端 → Gateway → 后端 LLM → 完整响应返回。
- 适用：文本补全、Embedding、短响应场景。

### 4.2 HTTP-SSE
- 流式请求：Gateway 透传 `text/event-stream`，按行转发后端数据流。
- 适用：ChatGPT 类逐字流式输出。

### 4.3 WebSocket
- 全双工长连接：Gateway 维护与客户端的 WS 连接，并代理到后端 WS 或转换为 SSE/HTTP。
- 适用：多轮对话、实时语音、持续会话场景。

## 5. 路由设计

路由决策链（Route Chain）：

```text
请求 → 协议识别 → 模型解析 → 染色体提取 → 灰度判定 → 路由策略 → 节点选择 → 转发
```

- **模型解析**：从 URL Path、Header（`X-Model`）或 Body 字段（`model`）识别目标模型。
- **路由策略**：
  - 权重随机
  - 标签匹配
  - 版本优先
  - 最小负载
  - 染色隔离（染色体优先命中染色池）

## 6. 灰度设计

灰度规则引擎支持多维度：

- 流量百分比
- Header / Query / Cookie 匹配
- 用户 ID 白名单 / 取模
- 模型版本号

命中灰度后，请求路由到新版节点池，并记录 `X-Canary: true`。

## 7. 染色体设计

染色体（Traffic Staining）用于给请求打标并沿链路透传：

- **染色来源**：Header（`X-Stain: experiment-a`）、Cookie、自动抽样。
- **染色规则**：命中规则后给请求附加染色标记。
- **染色路由**：染色流量优先/强制路由到染色专用节点池。
- **染色透传**：向下游后端、响应 Header、日志 Trace 中携带染色标记。
- **染色隔离**：避免染色流量污染基线流量，支持影子集群。

## 8. 技术栈

| 层 | 技术 |
|----|------|
| 框架 | Spring Boot 3.x + Spring WebFlux（Reactive，适合高并发流式转发） |
| 文档 | SpringDoc OpenAPI + Swagger UI |
| 协议 | WebFlux RouterFunction / WebSocketHandler / SSE Emitter |
| 客户端 | WebClient（支持 HTTP/SSE 流式响应） |
| 配置 | 本地 YAML + 可选 Nacos / Apollo（后续扩展） |
| 限流 | Bucket4j 或 Redis + Lua（后续扩展） |
| 监控 | Micrometer + Prometheus（后续扩展） |
| 测试 | JUnit 5 + WebTestClient + Mockito |

## 9. 模块划分（Maven 多模块）

```text
model-gateway/
├── model-gateway-client          # Client 层：DTO、API、错误码
├── model-gateway-adapter         # Adapter 层：Controller / WS / SSE
├── model-gateway-application     # Application 层：用例编排
├── model-gateway-domain          # Domain 层：领域模型、策略、服务
├── model-gateway-infrastructure  # Infrastructure 层：客户端、配置、存储
├── model-gateway-bootstrap       # 启动模块：Spring Boot 主入口、配置
└── docs                          # 架构与设计文档
```

## 10. 演进路线

### Phase 1：MVP（本次编码目标）
- 多模块 COLA 骨架搭建
- HTTP / SSE / WebSocket 转发通道
- 内存路由表与基础路由策略
- API Key 认证 + 简单限流
- 超时控制 + 统一异常处理
- Swagger 文档

### Phase 2：高可用增强
- 注册中心集成（Nacos / Consul）
- 动态路由与热更新
- Redis 分布式限流
- 全链路追踪

### Phase 3：企业级能力
- 多租户隔离
- 模型熔断与降级
- 计费与配额
- Admin 控制台

## 11. 已确认设计决策

详见 [`docs/05-design-decisions.md`](file:///E:/ai-trae/github/model-gateway/docs/05-design-decisions.md)。

| 问题 | 决策 |
|------|------|
| 模块组织 | **Maven 多模块** COLA 分层 |
| 后端协议 | **OpenAI 兼容协议** |
| 路由配置 | **YAML 静态配置** + 热刷新接口 |
| 限流 | 第一版 **本地内存限流** |
| WebSocket | **WS↔WS 双端代理** |
| 灰度 + 染色体 | **本次一并实现** |
| 可观测 | 补齐 **OpenTelemetry Trace** |
| 范围 | 补齐 **P0 + P1** |
| 文档 | 必须具备 **Swagger** |

## 12. 下一步行动

开始按 COLA 架构编码实现，模块包括 `client`、`domain`、`application`、`infrastructure`、`adapter`、`bootstrap`。
