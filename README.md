# 研发故障排查多 Agent 平台

这是一个使用 Java 和 Spring Boot 构建的故障排查平台，连接事件接收、指标和日志采集、上下文分析、根因分析、修复方案生成、人工审批和审计记录。

## 当前流程

```text
POST /api/incidents
  -> TriageAgent：判断严重程度和影响范围
  -> ContextAgent：收集 Prometheus 指标和 Loki 日志
  -> RcaAgent：分析可能的根因
  -> RemediationAgent：生成并校验修复计划
  -> 人工审批：APPROVED 或 REJECTED
```

如果修复 Agent 返回不安全或无法识别的命令，事件会进入 `ESCALATED`。系统不会自动执行模型生成的命令。

## 技术组件

- Java 25、Spring Boot 3.5、Maven
- MySQL 8：保存事件、证据、RCA、修复计划和审计记录
- Prometheus：保存和查询指标
- Loki：保存和查询日志
- Grafana Alloy：把 Spring Boot 日志发送到 Loki
- LangChain4j：调用 DeepSeek 等 OpenAI-compatible 模型

## 启动

项目使用本机 MySQL，不需要创建 Docker MySQL。数据库名为 `incident_platform`。数据库配置在 `src/main/resources/application.yml`，生产环境应通过 `INCIDENT_DB_USERNAME` 和 `INCIDENT_DB_PASSWORD` 设置账号密码。

启动观测容器：

```powershell
docker start prometheus
docker start loki
docker start alloy
docker ps
```

配置文件位于 `observability/prometheus/prometheus.yml` 和 `observability/alloy/config.alloy`。

启动 Spring Boot：

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:INCIDENT_API_KEY = [Convert]::ToBase64String($bytes)
$rng.Dispose()
Write-Host "Save this API key for your requests: $env:INCIDENT_API_KEY"

mvn spring-boot:run
```

`INCIDENT_API_KEY` 是保护业务 API 的密钥。请保存刚生成的值，不要提交到 Git。若从 IntelliJ 启动应用，请在 Run Configuration 的 Environment variables 中设置 `INCIDENT_API_KEY`。

在另一个 PowerShell 窗口中，把刚才保存的密钥填入 `$apiKey`，后续 API 请求都通过 `X-API-Key` 请求头携带它：

```powershell
$apiKey = "把刚才生成的密钥粘贴到这里"
$headers = @{ "X-API-Key" = $apiKey }
```

所有 `/api/**` 接口都需要这个请求头。未配置服务器密钥时，业务 API 返回 `503`；请求没有提供正确密钥时，返回 `401`。Actuator 健康检查和 Prometheus 指标接口不受此密钥保护，方便监控工具采集。此 API key 是所有调用方共用的门锁，不区分具体人员；不要通过公网明文 HTTP 暴露服务，正式远程部署还需要 HTTPS 和更细的用户权限。

健康检查：

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/health" |
    ConvertTo-Json -Depth 5
```

API 交互文档（页面中接口标题和说明为中文）：

- 浏览器访问 `http://localhost:8080/swagger-ui/index.html` 查看并试用业务接口；如需把 Swagger 自带的按钮也翻译成中文，可以在 Edge 页面上右键并选择“翻译成中文”；
- 原始 OpenAPI 说明位于 `http://localhost:8080/v3/api-docs`；
- 在 Swagger UI 中点击 **Authorize**，填入 API key 的值（不用加 `Bearer` 前缀），之后再试 `/api/**` 接口。

## 登记项目并创建事件

创建故障前，先登记项目。服务会返回一个稳定的项目 ID；之后提交该项目的故障都使用这个 ID。不同项目使用不同 ID，后续知识检索就可以按 ID 限定范围。

```powershell
$projectBody = @{ name = "求职 Agent" } | ConvertTo-Json
$project = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/projects" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($projectBody))

$project | ConvertTo-Json -Depth 5
$projectId = $project.id
```

也可以通过 `GET /api/projects` 查看已登记项目。创建事件时必须带上对应的 `projectId`；未登记的项目 ID 会返回 HTTP `404`。

## 管理项目知识资料

每个项目都可以单独添加、查看、修改和删除纯文本知识资料。当前阶段先保存资料原文，还没有做向量化和 RAG 检索；后续会在检索时按项目 ID 隔离资料。

在 Swagger UI 中使用 `POST /api/projects/{projectId}/knowledge-documents` 添加资料，请把 `{projectId}` 换成项目 ID，请求体例如：

```json
{
  "title": "MySQL 连接故障排查手册",
  "content": "1. 确认 MySQL 服务正在运行。\n2. 检查数据库地址、端口、用户名和密码。\n3. 查看应用日志中的数据库连接错误。"
}
```

对应项目的资料可以通过 `GET /api/projects/{projectId}/knowledge-documents` 查看；单份资料支持 `GET`、`PUT` 和 `DELETE`。资料只会出现在 URL 指定项目的列表中。

## 创建事件

```powershell
$body = @{
    projectId  = $projectId
    title       = "Full observability acceptance test"
    service     = "incident-platform"
    environment = "local"
    severity    = "P2"
    description = "Verify Prometheus, Loki and MySQL end to end"
} | ConvertTo-Json

$result = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/incidents" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$result | ConvertTo-Json -Depth 10
```

成功结果中，`evidence.items` 应包含 `prometheus` 和 `loki`，并返回事件 `id`。

## 查询和审批

查询事件列表时，可以用 `createdFrom` 和 `createdTo` 按创建时间筛选。时间使用 ISO-8601 格式；两个端点都包含在结果中。比如查询 2026 年 10 月 1 日至 10 月 10 日创建的事件：

```powershell
Invoke-RestMethod `
    -Method Get `
    -Uri "http://localhost:8080/api/incidents?createdFrom=2026-10-01T00:00:00Z&createdTo=2026-10-10T23:59:59Z" `
    -Headers $headers |
    ConvertTo-Json -Depth 10
```

可以只提供起点或终点。若 `createdFrom` 晚于 `createdTo`，接口返回 HTTP `400 Bad Request`。

```powershell
$id = "把创建事件返回的 id 粘贴到这里"

Invoke-RestMethod `
    -Method Get `
    -Uri "http://localhost:8080/api/incidents/$id" `
    -Headers $headers |
    ConvertTo-Json -Depth 10
```

只有 `AWAITING_APPROVAL` 状态的事件可以审批：

```powershell
$approvalBody = @{
    approved = $true
    comment  = "人工确认，允许执行低风险处理步骤"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/incidents/$id/approval" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($approvalBody)) |
    ConvertTo-Json -Depth 10
```

把 `approved` 改为 `$false` 会进入 `REJECTED`，并记录 `remediation.rejected` 审计动作。状态不允许审批时，接口返回 HTTP `409 Conflict`。

## MySQL 查询

进入 MySQL 后执行：

```sql
USE incident_platform;

SELECT id, title, service, status, created_at
FROM incidents
WHERE id = '事件ID';

SELECT JSON_PRETTY(evidence_json) AS evidence
FROM incidents
WHERE id = '事件ID';

SELECT JSON_PRETTY(audit_json) AS audit
FROM incidents
WHERE id = '事件ID';
```

## 测试

```powershell
mvn test
```

测试需要 Docker Desktop 正在运行。MySQL 仓储集成测试会自动启动一个临时 `mysql:8.4` 容器，测试结束后自动清理；它不会连接或清空你本机的 `incident_platform` 数据库。

当前测试覆盖 Prometheus、Loki、ContextAgent、RCA、Remediation 安全校验、`ESCALATED` 升级、`APPROVED` 和 `REJECTED` 审批、非法状态返回 `409`，项目登记和故障关联，以及真实 MySQL 容器上的读写、筛选、时间范围和分页。

推送代码或创建 Pull Request 后，GitHub Actions 会自动运行同一套 `mvn test` 测试。

## 目录说明

- `domain`：事件、证据、RCA、修复计划和状态
- `agent`：Triage、Context、RCA、Remediation Agent
- `service`：多 Agent 编排和状态流转
- `adapter`：HTTP、MySQL、Prometheus、Loki 适配器
- `port`：可替换的仓储和证据源接口
- `observability`：Prometheus 和 Alloy 配置
- `src/test`：自动化测试

## 安全边界

- LLM 只能生成修复建议，不能直接执行命令；
- 只有白名单占位符命令才能进入修复计划；
- 不安全方案会升级为 `ESCALATED`；
- 只有 `AWAITING_APPROVAL` 的事件可以审批；
- 关键状态变化都会写入审计记录。
