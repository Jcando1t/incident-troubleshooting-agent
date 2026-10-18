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

上下文调查阶段使用一个受限的 `InvestigationAgent`：大模型只能在已注册的只读证据工具中选择下一步，或返回 `STOP`；每个工具最多调用一次，默认最多调查 3 轮。未知工具选择会立即停止，系统不会执行模型生成的命令。整体顺序、超时、状态升级和人工审批仍由 Workflow 控制。

每次调查的选择和结果会保存在事件的 `evidence.investigationTrace` 中，并同步写入审计记录，例如 `investigation.tool.completed`、`investigation.tool.rejected` 和 `investigation.tool.stopped`，因此可以回看 Agent 为什么选择或停止。

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

在没有旧容器的新环境中，启动观测容器：

```powershell
Set-Location "path\to\incident-troubleshooting-agent"
docker compose -f observability/docker-compose.yml up -d
docker ps
```

停止 Compose 创建的观测容器（保留 Prometheus、Loki 和 Alloy 数据卷）：

```powershell
docker compose -f observability/docker-compose.yml down
```

配置文件位于 `observability/prometheus/prometheus.yml`、`observability/alloy/config.alloy` 和
`observability/docker-compose.yml`。Compose 不会启动 MySQL；项目继续使用本机 MySQL。
如果本机已经有手动创建的 `prometheus`、`loki`、`alloy` 容器，它们会占用相同端口。不要同时启动两套；
继续使用已有容器时可运行 `docker start prometheus loki alloy`。迁移到 Compose 前，先停止旧容器
（`docker stop prometheus loki alloy`），不会删除旧容器；之后再执行上面的 Compose 命令。

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

外部服务调用默认带有超时，避免 Prometheus、Loki、Ollama 或 DeepSeek 无响应时一直占用请求线程。可按部署环境通过环境变量调整：`OBSERVABILITY_CONNECT_TIMEOUT`（默认 3 秒）、`OBSERVABILITY_READ_TIMEOUT`（默认 5 秒）、`OLLAMA_CONNECT_TIMEOUT`（默认 3 秒）、`OLLAMA_READ_TIMEOUT`（默认 15 秒）、`INCIDENT_LLM_TIMEOUT`（默认 30 秒）和 `INCIDENT_LLM_MAX_RETRIES`（默认重试 1 次）。例如 PowerShell 中可设置 `$env:INCIDENT_LLM_TIMEOUT = "60s"`。Prometheus/Loki 查询失败和 Ollama 检索失败会记录警告并降级；Triage、RCA 或修复方案阶段失败会把事件保存为 `ESCALATED`，并在审计记录中写入失败阶段。

在另一个 PowerShell 窗口中，安全输入应用运行配置中的 API key。后续 API 请求都通过 `X-API-Key` 请求头携带它；使用 `Read-Host -AsSecureString` 可以避免密钥直接出现在命令文本和命令历史中：

```powershell
$secureApiKey = Read-Host "请输入 API Key" -AsSecureString
$apiKey = [System.Net.NetworkCredential]::new("", $secureApiKey).Password
$headers = @{ "X-API-Key" = $apiKey }
```

演示结束后清除当前 PowerShell 会话中的变量：

```powershell
Remove-Variable apiKey, secureApiKey, headers
```

所有 `/api/**` 接口都需要这个请求头。未配置服务器密钥时，业务 API 返回 `503`；请求没有提供正确密钥时，返回 `401`。Actuator 健康检查和 Prometheus 指标接口不受此密钥保护，方便监控工具采集。此 API key 是所有调用方共用的门锁，不区分具体人员；不要通过公网明文 HTTP 暴露服务，正式远程部署还需要 HTTPS 和更细的用户权限。

健康检查：

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/health" |
    ConvertTo-Json -Depth 5
```

API 交互文档（页面中接口标题和说明为中文）：

- 浏览器访问 `http://localhost:8080/swagger-ui/index.html` 查看并试用业务接口；如需把 Swagger 自带的按钮也翻译成中文，可以在 Edge 页面上右键并选择“翻译成中文”；
- 浏览器访问 `http://localhost:8080/` 使用中文控制台：输入 API Key 后可以选择项目、查看/筛选事件、创建事件、查看分析详情并审批修复方案；
- 在中文控制台中可以删除当前项目，但只有没有事件和知识资料的空项目允许删除；`Legacy Project` 是兼容旧数据的系统项目，不能删除；
- 原始 OpenAPI 说明位于 `http://localhost:8080/v3/api-docs`；
- 在 Swagger UI 中点击 **Authorize**，填入 API key 的值（不用加 `Bearer` 前缀），之后再试 `/api/**` 接口。

## 本地文字向量模型（Ollama）

Ollama 在本机运行 embedding 模型。当前配置使用适合多语言检索的 `bge-m3`；它把文字变成数字向量，和 DeepSeek 的聊天分析职责不同。默认地址为 `http://localhost:11434`，可以通过 `OLLAMA_BASE_URL` 和 `OLLAMA_EMBEDDING_MODEL` 环境变量调整。

安装并启动 Ollama 后，在 PowerShell 下载模型：

```powershell
ollama pull bge-m3
ollama list
```

在 Swagger 中调用 `POST /api/projects/{projectId}/knowledge-documents/{documentId}/embedding/index`，系统会把资料分成小段、调用 Ollama 生成向量，并保存到 MySQL。成功响应会显示模型名称和分段数量。资料新建后要调用这个接口建立向量索引；资料修改后旧索引会清除，需要重新调用。

创建故障事件时，RCA 流程会用事件标题、服务名和描述生成查询向量，只从该事件的 `projectId` 对应资料中找最相似的 3 个片段，并把片段作为带来源引用的证据交给 RCA Agent。当前项目没有已建立索引的资料时，检索结果为空；Ollama 不可用时会记录警告并继续原来的故障分析流程。

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

每个项目都可以单独添加、查看、修改和删除纯文本知识资料。资料原文保存在 MySQL 中；调用 embedding 索引接口后，系统会按文本切片，使用 Ollama 的 `bge-m3` 生成向量，并把向量切片保存到 MySQL。资料修改后旧向量会被清除，需要重新建立索引；资料删除时对应的向量切片也会随之删除。

故障分析时，系统只检索事件 `projectId` 对应项目的已索引资料，不会把其他项目的资料混在一起。也可以使用 `POST /api/projects/{projectId}/knowledge-search` 单独预览检索结果；该接口只返回匹配片段和相似度，不会创建故障事件。

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

查询事件列表时，可以使用 `projectId` 限定某个项目，也可以用 `createdFrom` 和 `createdTo` 按创建时间筛选。时间使用 ISO-8601 格式；两个时间端点都包含在结果中。还支持 `status`、`service`、`limit` 和 `offset`。比如按项目和时间范围查询：

```powershell
Invoke-RestMethod `
    -Method Get `
    -Uri "http://localhost:8080/api/incidents?projectId=$projectId&createdFrom=2026-10-01T00:00:00Z&createdTo=2026-10-10T23:59:59Z" `
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

如果希望同时确认事件属于某个项目，可以附带 `projectId`：

```powershell
Invoke-RestMethod `
    -Method Get `
    -Uri "http://localhost:8080/api/incidents/$id?projectId=$projectId" `
    -Headers $headers |
    ConvertTo-Json -Depth 10
```

带上 `projectId` 后，项目不存在或事件不属于该项目都会返回 `404`，不会暴露跨项目事件。

只有 `AWAITING_APPROVAL` 状态的事件可以审批：

```powershell
$approvalBody = @{
    approved = $true
    comment  = "人工确认，允许执行低风险处理步骤"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/incidents/$id/approval?projectId=$projectId" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($approvalBody)) |
    ConvertTo-Json -Depth 10
```

把 `approved` 改为 `$false` 会进入 `REJECTED`，并记录 `remediation.rejected` 审计动作。状态不允许审批时，接口返回 HTTP `409 Conflict`。

审批请求带上 `projectId` 时，系统会先验证项目存在，再验证事件属于该项目；项目不存在或事件不属于项目时返回 `404`。

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
