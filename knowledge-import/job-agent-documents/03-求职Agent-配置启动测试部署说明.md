适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
## 配置项一览（来源：app/config.py + .env.example〔配置确认〕）
| 配置项 | 作用 | 必填 | 默认值 | 安全占位符示例 |
|---|---|---|---|---|
| APP_ENV | 运行环境标记 | 否 | local | APP_ENV=local |
| DATABASE_PATH | SQLite 数据库文件路径（相对项目根或绝对） | 否 | data/job_agent.db | DATABASE_PATH=data/job_agent.db |
| FILE_STORAGE_DIR | 简历原稿/图片私有文件目录 | 否 | data/files | FILE_STORAGE_DIR=data/files |
| DEEPSEEK_API_KEY | DeepSeek 密钥（AI 改进必需） | 用 AI 时必填 | 空 | DEEPSEEK_API_KEY=<YOUR_API_KEY> |
| DEEP_SEEK_API_KEY | 同上的备用环境变量名（二选一即可） | 否 | 空 | DEEP_SEEK_API_KEY=<YOUR_API_KEY> |
| DEEPSEEK_BASE_URL | 模型 API 地址 | 否 | https://api.deepseek.com | DEEPSEEK_BASE_URL=https://api.deepseek.com |
| DEEPSEEK_MODEL | 模型名（需支持 JSON mode） | 否 | deepseek-chat | DEEPSEEK_MODEL=deepseek-chat |
| LLM_TIMEOUT_SECONDS | 模型调用超时秒数（必须为正数，否则启动报错） | 否 | 30 | LLM_TIMEOUT_SECONDS=30 |
| LLM_USE_ENV_PROXY | 是否继承系统代理（httpx trust_env） | 否 | 0 | LLM_USE_ENV_PROXY=0 |
| CHROME_EXECUTABLE | Chrome 可执行文件路径（找不到 Chrome 时设置） | 否 | 自动探测常见安装路径 | CHROME_EXECUTABLE=C:\path\to\chrome.exe |

说明：示例配置 .env.example 中的空值是占位符，不是真实值。本项目无数据库密码（SQLite 文件型，`<YOUR_DATABASE_PASSWORD>` 不适用）；无 Token/Cookie 类配置。

## 本地开发环境准备
1. 安装 Python（README 目标 3.11；当前 .venv 为 3.12.10，均可用）〔待定：以你机器为准〕
2. 安装 Node.js（前端构建需要，Vite 6 建议 Node 18+）
3. 安装 Google Chrome（BOSS 同步功能需要）
4. 复制 `.env.example` 为 `.env`，按需填写 `DEEPSEEK_API_KEY=<YOUR_API_KEY>`

## 依赖安装
```powershell
python -m pip install -r requirements.txt
# 前端
cd frontend
npm install
```

## 数据库初始化
- 无需手动建库：应用启动时自动执行 Alembic `upgrade head`（main.py lifespan）并自动创建 data 目录〔代码确认〕
- 手动初始化空库：`python -m alembic upgrade head`

## 数据库迁移
```powershell
python -m alembic upgrade head   # 应用所有迁移
python -m alembic current        # 查看当前版本
python -m alembic stamp head     # 仅登记既有数据库基线（不建表不删表，需先备份并确认结构一致）
```
⚠️ 不要对已存在但未登记的数据库直接运行 upgrade（README 明确警告）。

## 启动后端
```powershell
python -m uvicorn main:app --reload
# 或生产式本地运行
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```
访问 http://127.0.0.1:8000；健康检查 GET /health。

## 启动前端
```powershell
cd frontend
npm run dev        # 开发模式 http://127.0.0.1:5173，/api 自动代理到 8000
# 或构建后由后端直接服务
npm run typecheck
npm run build      # 产物 frontend/dist
```
注意：frontend/dist 在 .gitignore 中，克隆后必须先 build 一次，否则 FastAPI 回退到旧版 Jinja 页面（app/templates/index.html）〔代码确认〕。

## 启动外部服务
- DeepSeek：云服务，无需本地启动；确认 .env 中 Key 有效即可
- Chrome（BOSS 同步）：在应用页面点击"启动并打开 BOSS"自动拉起（独立用户目录 data/chrome_profile，CDP 端口 9222）；用户在弹出的 Chrome 中手动登录，应用不保存密码〔代码确认〕

## 运行测试
```powershell
# README 推荐（标准库）
python -m unittest discover -s tests -v
# 项目同时提供 pytest.ini，也可用
python -m pytest tests -v
```
测试覆盖：迁移、配置、数据库、JD 分析、检索器、LLM 客户端、简历 Agent、质量检查、导出、文档解析、BOSS 解析、Chrome 自动化、存储、端到端工作流（test_resume_workflow_e2e.py）等 20 个文件〔测试确认〕。说明：项目未明确区分"单元测试/集成测试"目录，端到端流程测试与单元测试混在 tests/ 中〔代码确认〕。

## 构建项目
- 后端：无需构建（Python 直接运行）
- 前端：`npm run build`（见上）

## 查看日志
- 后端日志输出在 uvicorn 控制台（无文件日志配置——当前项目未发现日志文件/日志框架配置）
- Chrome 调试输出被丢弃（subprocess DEVNULL）；data/ 下有历史排查用 stdout/stderr 日志文件残留，非运行时产物
- 任务级错误：GET /api/resume-runs 查看 status=failed 记录的 error_message（可持久化、可读）〔代码确认〕

## 停止服务
- 后端：运行 uvicorn 的终端按 Ctrl+C
- Chrome：直接关闭浏览器窗口（独立进程，不随后端退出而关闭——代码有意 DETACHED 处理）

## 常见启动错误
见文档 5 故障 1/2/4/5（依赖缺失、端口占用、迁移失败、Key 未配置）

## 适用范围与部署限制
- 当前项目适合**个人单机使用**：无登录鉴权、owner_id 硬编码、SQLite 单写者、监听 127.0.0.1、密钥本地 .env〔代码确认〕
- 生产部署限制：未做容器化/CI/反向代理/HTTPS/多 worker 验证；若多人使用需补：认证体系、数据库切换（如 PostgreSQL）、CORS、密钥管理、日志与监控〔待定：均为未实现项〕
- 敏感数据约束：.env、data/、frontend/dist、.venv 均在 .gitignore，不得提交 Git〔配置确认〕