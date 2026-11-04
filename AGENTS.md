# AGENTS.md — model-gateway 开发协作指南

## 1. 项目定位

`model-gateway` 是一个基于 **COLA 4.0 分层架构** 的大模型（LLM）统一网关，侧重：

- 流量转发：HTTP / SSE / WebSocket 三协议接入与透传
- 路由：权重、标签、版本、组合策略
- 灰度：百分比、Header/Cookie、用户白名单
- 染色体：请求染色、透传、强制路由
- 接入层：API Key 认证、模型授权、本地内存限流
- 可观测：OpenTelemetry Trace、RequestId/TraceId 全链路透传
- 协议兼容：OpenAI `/v1/chat/completions`

## 2. 模块结构（COLA）

```
model-gateway
├── model-gateway-client      # Client 层：DTO、API 契约、错误码、常量
├── model-gateway-domain      # Domain 层：聚合根、实体、值对象、领域服务、策略、仓库接口
├── model-gateway-application # Application 层：应用服务、命令/结果对象、编排
├── model-gateway-infrastructure # Infrastructure 层：配置、WebClient、健康检查、限流、OTel、仓库实现
├── model-gateway-adapter     # Adapter 层：HTTP/SSE/WS 入口、过滤器、异常处理、WebSocket 处理器
└── model-gateway-bootstrap   # Bootstrap 层：Spring Boot 启动入口、application.yml
```

依赖方向：bootstrap → adapter → application → domain → client；infrastructure 实现 domain 接口，被 bootstrap 组合。

## 3. 核心入口

| 协议 | 路径 | 说明 |
|------|------|------|
| HTTP | `POST /v1/chat/completions` | OpenAI 兼容同步接口 |
| SSE  | `POST /v1/chat/completions`（`stream=true`） | 流式转发 |
| WS   | `ws://host/ws/v1/chat?model=deep-seek-4.1-flash` | WebSocket 双端代理 |
| Admin| `GET /admin/routes` | 查看路由表 |
| Admin| `GET /admin/endpoints/health` | 查看节点健康状态 |
| Admin| `GET /admin/canary/rules` | 查看灰度规则 |
| Admin| `GET /admin/stain/rules` | 查看染色体规则 |
| Docs | `/swagger-ui.html` | Swagger UI |
| Docs | `/v3/api-docs` | OpenAPI JSON |

## 4. 开发约定

### 4.1 包规范

- 领域对象优先不可变，使用 Lombok `@Builder`、`@Value` 或 final 字段。
- 应用服务只做编排，不写业务规则；业务规则放在 domain service / policy。
- 仓库接口定义在 domain，实现放在 infrastructure。
- adapter 只负责协议转换，不直接调用 infrastructure 实现。

### 4.2 错误码

统一错误码在 `GatewayErrorCode`：

- `GW-0000` 成功
- `GW-0001` API Key 无效
- `GW-0002` 触发限流
- `GW-0003` 模型不存在
- `GW-0004` 无可用后端节点
- `GW-0005` 路由失败
- `GW-0006` 后端转发异常（HTTP 502）
- `GW-0007` 协议不支持
- `GW-0008` 请求体过大
- `GW-0009` 无权访问该模型
- `GW-0010` 请求参数非法
- `GW-9999` 网关内部错误

### 4.3 Trace / RequestId

- 每个请求必须生成 `X-Request-Id` 并回写响应头。
- `X-Trace-Id` 由 OpenTelemetry SDK 生成，真实 traceId 会打印到日志（LoggingSpanExporter）。
- 向下游转发时通过 W3C Trace Context 注入 `traceparent`。

## 5. 构建与运行

```bash
# 全量编译安装
mvn clean install -DskipTests

# 启动服务
cd model-gateway-bootstrap
mvn spring-boot:run
```

服务默认监听 `8080`，配置文件：`model-gateway-bootstrap/src/main/resources/application.yml`。

## 6. 本地验证

```bash
# 查看路由表
curl http://localhost:8080/admin/routes

# Swagger UI
open http://localhost:8080/swagger-ui.html

# 同步请求（需配置可用后端）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer mg-demo-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"deep-seek-4.1-flash","messages":[{"role":"user","content":"hi"}]}'

# WebSocket 验证（需安装 websocket-client：pip install websocket-client）
python -c "
import websocket, json
ws = websocket.create_connection(
    'ws://localhost:8080/ws/v1/chat?model=deep-seek-4.1-flash',
    header=['Authorization: Bearer mg-demo-key']
)
ws.send(json.dumps({
    'model': 'deep-seek-4.1-flash',
    'messages': [{'role': 'user', 'content': 'hi'}]
}))
print(ws.recv())
ws.close()
"
```

## 7. 常见排错

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 启动报 `ScopeConfigurator` 类找不到 | OpenTelemetry 版本与 Spring Boot BOM 冲突 | 根 POM 已用 `opentelemetry-bom` 统一版本 |
| Admin 接口 404 | adapter 包未被 Spring 扫描 | `GatewayAutoConfiguration` 已扩大 `@ComponentScan` |
| `/v3/api-docs/swagger-ui.html` 404 | 路径错误，Swagger UI 在 `/swagger-ui.html` | 访问正确地址 |
| 返回 `GW-0004` | 后端节点健康检查未通过 | 启动对应后端服务或检查 `/health` 路径 |
| 返回 `GW-9999` | 未捕获异常 | 查看日志，确认是否缺少依赖或 JSON 格式错误 |

## 7.1 WebSocket 心跳

WebSocket 长连接默认启用**协议层 ping/pong 心跳**，配置项：

```yaml
model-gateway:
  websocket:
    heartbeat-enabled: true       # 是否向客户端发 ping
    heartbeat-interval: 30s       # ping 间隔
    idle-timeout: 120s            # 空闲超时（收不到任何帧则断开）
    backend-heartbeat-enabled: true # 是否向后端发 ping
```

- 网关会定时向客户端和后端发送 `PING` 控制帧。
- 收到的 `PING/PONG` 帧不会透传到另一端，由 Netty 自动响应或本地过滤。
- 任意数据帧或控制帧都会刷新空闲计时；超过 `idle-timeout` 无帧到达则断开连接。

## 8. 扩展提示

- 新增路由策略：实现 `RouteStrategy` 接口并在 `CompositeRouteStrategy` 中注册。
- 新增限流维度：扩展 `RateLimitService` 与 `Resilience4jRateLimiter`。
- 新增可观测导出：替换 `LoggingSpanExporter` 为 OTLP / Jaeger Exporter。
- 动态配置：当前为 YAML 静态配置，后续可接入 Nacos / Apollo，替换 `YamlRouteConfigRepository`。
