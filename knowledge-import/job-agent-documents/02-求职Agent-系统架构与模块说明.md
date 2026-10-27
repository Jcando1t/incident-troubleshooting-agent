适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
## 各部分关系
- 浏览器（Vue 3 SPA 或 Jinja 兼容页）→ HTTP JSON → FastAPI（main.py 路由层）→ app/database.py（数据访问函数）→ SQLAlchemy ORM → SQLite（data/job_agent.db）。
- 简历改进链路额外经过：main.py → jd_analyzer / knowledge_retriever（本地计算）→ resume_graph（LangGraph）→ resume_agent → llm_client → DeepSeek API（OpenAI 兼容）。
- 文件（简历原稿、图片）以 UUID 随机文件名保存到 FILE_STORAGE_DIR（默认 data/files），数据库只存相对路径 storage_path〔代码确认：app/storage.py〕。
- Chrome 自动化独立于 uvicorn 进程（DETACHED_PROCESS 启动），经 CDP 端口 9222 通信，不保存密码/Cookie〔代码确认：chrome_automation.py〕。

## 后端主要模块及职责〔代码确认〕
| 模块 | 职责 |
|---|---|
| main.py（~725 行） | 全部 REST API 路由、lifespan（启动时 alembic upgrade head + 旧文件迁移）、前端静态托管 |
| app/config.py | 从 .env/环境变量读取配置（dataclass Settings，每次调用重新读取） |
| app/schemas.py | Pydantic 输入模型（TargetInput、JobInput、KnowledgeInput、ResumeRunInput 等 19 个） |
| app/models.py | SQLAlchemy 模型：JobTarget、Job、KnowledgeItem、Resume、ResumeSource、ResumeImageSource、AgentRun |
| app/database.py（~917 行） | 全部数据访问函数（每次操作新建 Engine）；save_approved_agent_run 单事务确认+建版本 |
| app/sqlalchemy_db.py | Engine 工厂，开启 SQLite 外键 |
| app/storage.py | 本地私有文件读写删（UUID 文件名、路径逃逸防护） |
| app/services/jd_analyzer.py | JD 本地规则拆解（职责/硬性要求/优先条件/关键词）+ 逐条要求证据匹配 |
| app/services/knowledge_retriever.py | 关键词检索：结构化资料排序 + 简历切片检索（含 fallback） |
| app/services/resume_graph.py | LangGraph 图定义与运行（可注入 draft_generator/quality_checker 便于测试替身） |
| app/services/resume_agent.py | 构造 system/user prompt、调用 LLM、校验 JSON 结构与证据编号合法性 |
| app/services/llm_client.py | DeepSeek 客户端：重试、超时、JSON 解析（剥代码围栏）、配置/调用异常分类 |
| app/services/resume_quality.py | 本地质量检查：STAR 要点检查、原文/草稿 diff、A4 一页行数估算（56 行/48 字符宽） |
| app/services/factuality_check.py | 草稿中"新出现的数字与技术名词"标记（仅此范围，非全面事实证明） |
| app/services/document_parser.py | TXT/MD/PDF/DOCX 文字提取（30,000 字符截断） |
| app/services/ocr_service.py | 本地 RapidOCR 图片识别（≤25M 像素） |
| app/services/resume_export.py | DOCX/PDF（单页强约束）/MD 导出 |
| app/services/boss_job_parser.py | 粘贴的 BOSS 页面文字规则清洗 |
| app/services/chrome_automation.py（~859 行） | CDP 客户端、Chrome 启动/连接、收藏同步、岗位页打开、招呼语预填/发送、简历附件发送 |
| app/services/private_file_migration.py | 启动时把旧数据库内嵌文件迁移到文件目录（幂等） |

## 重要类、接口、服务与数据访问层
- 服务层无 class（除 LLMClient、ChromeController、_CdpSocket），均为模块级函数〔代码确认〕
- 关键接口（路由）：/health；/api/targets；/api/jobs + /api/jobs/boss/*（preview、browser connect/open/open-job/sync、prepare-greeting/send-greeting/send-resume、chat-status）；/api/knowledge；/api/resumes + download?format=docx|pdf|md；/api/resume-sources(+upload)；/api/resume-images(+content/recognize)；/api/resume-runs(+draft/approve)〔代码确认：main.py〕
- 数据访问层：app/database.py 中每个实体一组 create/get/update/delete 函数，全部过滤 owner_id=="local"

## 请求完整路径（以"创建简历改进任务"为例）
1. 前端 POST /api/resume-runs（携带 allow_external_model 同意标志）
2. main.py 校验同意标志（否则 400）→ 校验原稿/岗位/方向存在性（404/409）
3. analyze_jd + match_jd_requirements（本地）→ retrieve_knowledge + retrieve_resume_chunks（本地）
4. 组装 input_snapshot → create_agent_run 落库（status=running）
5. run_resume_graph：LangGraph 依次执行 分析JD → 检索资料 → 生成草稿（LLM）→ 质量检查 → 条件路由（超页→needs_revision；否则 awaiting_review）
6. 成功：update_agent_run(status="draft"，保存结果/证据/缺口/质量检查/图状态) 返回前端
7. 失败：LLMConfigurationError→503、LLMError→502，run 置 failed 并保留 error_message
8. 用户在前端编辑草稿（PUT draft，后端重新质量检查）→ POST approve（一页校验通过后单事务创建新 Resume 版本，AgentRun 置 approved）〔代码确认：main.py + database.py〕

## 各 Agent / Workflow 节点职责〔代码确认：resume_graph.py〕
- analyze_jd：JD 分类 + 要求-证据匹配（结果可被 API 层预计算复用）
- retrieve_knowledge：关键词检索（API 层已检索则复用，避免重复排序）
- generate_draft：调用 DeepSeek 生成简历草稿 + 招呼语 + 证据 + 缺口
- quality_check：本地 STAR/事实/一页检查
- needs_revision / await_review：终止状态节点；条件边由"预计页数>1"决定
- 当前只有一个垂直流程图，无多 Agent 协作〔文档确认：README〕

## 确定与待定
- 确定：上表全部模块（均有代码与多数测试）
- 待定：① BOSS 同步对页面改版的健壮性；② LLM 返回质量随 DeepSeek 服务波动；③ unittest 与 pytest 双轨运行是否有隐性差异（pytest.ini 存在但 README 只写 unittest 命令）

## 一段话总结
这是一个跑在自己电脑上的求职工作台：你把简历、经历资料和心仪岗位录入本地 SQLite；点"AI 改进"并明确同意后，系统在本地把 JD 拆成要求、用关键词检索出相关经历片段，把这些"有编号的证据"发给 DeepSeek 生成草稿和招呼语；草稿回来先过本地质量检查（STAR、新数字/名词标记、A4 一页估算），超页会被打回；你在页面上并排对照、编辑、确认后才保存为新版本并可导出 DOCX/PDF。全程数据不出本机（除你同意后发给 DeepSeek 的检索片段），所有对外动作（发消息、发简历）都要你逐次确认。