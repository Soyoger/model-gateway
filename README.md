# model-gateway

> **声明：本项目完全由 AI Coding 生成，不具备任何商业价值，仅作为 AI 练手项目。**

基于 **COLA 4.0 分层架构** 的大模型（LLM）统一网关，支持 HTTP / SSE / WebSocket 三种接入方式，侧重流量转发、智能路由、接入层管控、灰度发布与染色体能力。

## 核心能力

- **三协议接入**：HTTP 同步、HTTP-SSE 流式、WebSocket 双端代理
- **OpenAI 兼容协议**：`/v1/chat/completions`、`/v1/models/{model}/completions`
- **智能路由**：权重、标签、版本、组合策略
- **灰度发布**：百分比、Header/Cookie、用户白名单
- **染色体**：请求染色、透传、强制路由到指定端点
- **接入层管控**：API Key 认证、模型授权、本地内存限流（Resilience4j）
- **可观测**：OpenTelemetry Trace、RequestId / TraceId 全链路透传
- **管理接口**：路由表、节点健康、灰度规则、染色体规则
- **接口文档**：SpringDoc + Swagger UI

## 技术栈

- Java 21
- Spring Boot 3.3.x + Spring WebFlux（Reactive 栈）
- Reactor Netty + WebClient
- COLA 4.0 六层架构
- Resilience4j 本地限流
- OpenTelemetry Trace
- SpringDoc OpenAPI / Swagger UI

## 项目结构

```
model-gateway
├── model-gateway-client         # DTO、API 契约、错误码、常量
├── model-gateway-domain         # 聚合根、实体、领域服务、策略、仓库接口
├── model-gateway-application    # 应用服务、命令/结果对象、编排
├── model-gateway-infrastructure # 配置、WebClient、健康检查、限流、OTel、仓库实现
├── model-gateway-adapter        # HTTP/SSE/WS 入口、过滤器、异常处理
└── model-gateway-bootstrap      # Spring Boot 启动入口、application.yml
```

详细架构与开发约定见 [AGENTS.md](./AGENTS.md)。

## 快速开始

### 1. 编译

```bash
mvn clean install -DskipTests
```

### 2. 启动

```bash
cd model-gateway-bootstrap
mvn spring-boot:run
```

服务默认监听 `8080`。

### 3. 查看接口文档

```bash
open http://localhost:8080/swagger-ui.html
```

OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 配置示例

配置文件位置：`model-gateway-bootstrap/src/main/resources/application.yml`

```yaml
model-gateway:
  api-keys:
    - key: mg-demo-key
      tenant: demo
      user-id: user-001
      allowed-models:
        - deep-seek-4.1-flash
        - deep-seek-4.1-pro
      rate-limit-qps: 100

  rate-limit:
    enabled: true
    limit-for-period: 100
    limit-refresh-period: 1s
    timeout-duration: 0s

  routes:
    - model: deep-seek-4.1-flash
      strategy: composite
      endpoints:
        - id: deepseek-flash-baseline
          base-url: https://api.deepseek.com
          version: v1
          weight: 90
        - id: deepseek-flash-canary
          base-url: https://api.deepseek.com
          version: v2
          weight: 10
          canary: true

    - model: deep-seek-4.1-pro
      strategy: composite
      endpoints:
        - id: deepseek-pro-baseline
          base-url: https://api.deepseek.com
          version: v1
          weight: 100

  canary-rules:
    - rule-id: deepseek-flash-v2-canary
      model: deep-seek-4.1-flash
      percentage: 10
      target-version: v2

  stain-rules:
    - rule-id: experiment-a
      stain-key: experiment-a
      model: deep-seek-4.1-flash
      sampling-rate: 5
      force-route: true
      target-endpoint-ids:
        - deepseek-flash-canary
```

## 主要接口

| 功能 | 方法 | 路径 |
|------|------|------|
| Chat Completions | POST | `/v1/chat/completions` |
| 按模型转发 | POST | `/v1/models/{model}/completions` |
| WebSocket 代理 | WS | `/ws/v1/chat?model=deep-seek-4.1-flash` |
| 路由表 | GET | `/admin/routes` |
| 节点健康 | GET | `/admin/endpoints/health` |
| 灰度规则 | GET | `/admin/canary/rules` |
| 染色体规则 | GET | `/admin/stain/rules` |
| 刷新路由 | POST | `/admin/routes/refresh` |

## 本地验证

```bash
# 查看路由表
curl http://localhost:8080/admin/routes

# 同步请求（需配置可用后端）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer mg-demo-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"deep-seek-4.1-flash","messages":[{"role":"user","content":"hi"}]}'

# SSE 流式
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer mg-demo-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"deep-seek-4.1-flash","stream":true,"messages":[{"role":"user","content":"hi"}]}'

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

## 错误码

| 错误码 | 含义 |
|--------|------|
| GW-0000 | 成功 |
| GW-0001 | API Key 无效或已禁用 |
| GW-0002 | 请求触发限流 |
| GW-0003 | 模型不存在 |
| GW-0004 | 无可用后端节点 |
| GW-0005 | 路由失败 |
| GW-0006 | 后端转发异常（HTTP 502） |
| GW-0007 | 协议不支持 |
| GW-0008 | 请求体大小超过限制 |
| GW-0009 | 当前 Key 无权访问该模型 |
| GW-0010 | 请求参数非法 |
| GW-9999 | 网关内部错误 |

## 文档目录

- [docs/01-architecture-plan.md](./docs/01-architecture-plan.md) — 架构规划
- [docs/02-detailed-design.md](./docs/02-detailed-design.md) — 详细设计
- [docs/03-technology-stack.md](./docs/03-technology-stack.md) — 技术选型
- [docs/04-gateway-capability-gap.md](./docs/04-gateway-capability-gap.md) — 网关能力检查与补缺
- [docs/05-design-decisions.md](./docs/05-design-decisions.md) — 关键设计决策
- [AGENTS.md](./AGENTS.md) — AI 协作开发指南

## License

[MIT](./LICENSE)
