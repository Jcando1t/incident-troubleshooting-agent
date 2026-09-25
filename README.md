# 研发故障排查多 Agent 平台

这是一个 Java/Spring Boot 技术栈的故障排查平台初始骨架，目标是把告警、观测证据、专家 Agent、RCA、修复审批和复盘串成一条可审计流程。

## 当前版本

当前实现使用内存存储和确定性 Agent，先跑通核心领域流程：

```text
告警接入 -> TriageAgent -> ContextAgent -> RcaAgent -> RemediationAgent -> 人工审批
```

Agent 通过接口隔离，后续可接入 OpenAI-compatible、Azure、Ollama 或企业内部模型；观测数据通过 `EvidenceSource` 接入 Prometheus、Loki、Elasticsearch、Jaeger、GitHub 等系统。

## 启动

需要 JDK 21+ 和 Maven 3.9+：

```bash
mvn spring-boot:run
```

创建事件：

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H 'Content-Type: application/json' \
  -d '{"title":"payment-service 5xx 上升","service":"payment-service","environment":"production","severity":"P1","description":"5xx 从 0.1% 上升到 18%"}'
```

查询事件：

```bash
curl http://localhost:8080/api/incidents/{incidentId}
```

审批修复计划：

```bash
curl -X POST http://localhost:8080/api/incidents/{incidentId}/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"comment":"已确认变更窗口"}'
```

## 目录

- `domain`：事件、证据、RCA、修复计划等领域对象
- `agent`：各个专家 Agent 和编排器
- `adapter`：HTTP API 与内存仓储
- `port`：模型、观测和持久化的替换点

## 后续路线

1. PostgreSQL + Flyway 持久化事件状态和审计记录
2. OpenTelemetry、Prometheus、Loki、Jaeger 适配器
3. LLM Gateway 与结构化输出校验
4. Kafka/Temporal 异步编排与重试
5. Slack/钉钉/企业微信通知及 RBAC
6. 修复动作白名单、dry-run、回滚和策略引擎
