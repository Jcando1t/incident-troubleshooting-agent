适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
## 项目名称
- 求职 Agent（FastAPI 实例 title="求职 Agent"；前端 package 名 "job-agent-frontend"）〔代码确认〕

## 项目目标
- 帮助求职者维护个人求职知识，理解岗位 JD，检索有依据的经历，在用户审核后生成并保存定制简历材料；核心原则是"事实依据 + 人工确认"，防止 AI 编造经历〔文档确认：docs/第一版范围.md〕
- 第一版仅面向项目创建者本人本地使用〔文档确认〕

## 主要用户
- 项目创建者本人（单用户）。所有数据表有 owner_id 字段但硬编码为 "local"，无登录体系〔代码确认：app/database.py〕

## 当前项目完成情况
- docs/第一版范围.md 规划的阶段一至阶段九均已实现并有对应代码与测试〔代码确认 + 测试确认〕；项目处于"可日常本地使用"状态，未做生产化部署〔代码确认：无部署配置〕

## 当前已经实现的功能（依据 README + main.py 路由代码确认）
- 求职方向 CRUD、启用/暂停
- 岗位 CRUD、状态/备注/下一步行动、重复保存拒绝（409）
- BOSS 岗位文字粘贴解析导入；专用 Chrome（CDP）登录后同步收藏列表/岗位详情页，预览后逐条确认导入
- 结构化个人知识库（教育/工作/项目/技能/成果等分类）CRUD
- 简历原稿：粘贴文本、上传 TXT/MD/PDF/DOCX 解析、PNG/JPG 图片本地 OCR（校对后确认）
- AI 简历改进：LangGraph 图（分析 JD → 检索资料 → 生成草稿 → 质量检查 → 路由到等待审核/需要修改），含证据引用、缺口提示、A4 一页篇幅硬约束
- 待审核草稿可编辑（重新质量检查）、刷新恢复、失败保留状态与错误信息
- 审核保存版本、版本号自增、DOCX/PDF/MD 导出
- 打招呼语生成；岗位定制简历（JD 快照随版本保存）
- BOSS 沟通页打开、招呼语预填、简历附件发送（均需用户确认）

## 当前尚未实现或不稳定的功能
- 向量/语义检索、Embedding：当前项目未发现（有意为之，README 明确说明首版为关键词式 RAG）〔代码确认 + 文档确认〕
- 多用户/登录/权限：未发现〔代码确认〕
- 自动投递：有意不做，所有对外发送需用户确认〔代码确认：confirmed 字段校验〕
- 生产部署（Docker/CI/反向代理）：当前项目未发现
- 不稳定点：BOSS 页面结构变化会导致 Chrome 同步/解析失效（解析基于 DOM 规则，属外部依赖风险）〔待定：取决于 BOSS 前端改版〕
- OCR 识别质量依赖本地 RapidOCR 模型，图片模糊时识别差，需人工校对〔代码确认：设计上要求校对〕

## 技术栈明细

| 项 | 内容 | 确认依据 |
|---|---|---|
| 后端语言 | Python；项目 .venv 实际为 Python 3.12.10（README 写"目标 3.11"，两者均可用）〔待定：以实际运行环境为准〕 | .venv 解释器版本 + README |
| 后端框架 | FastAPI >=0.115,<1.0，uvicorn 作服务器，Pydantic 模型校验 | requirements.txt〔配置确认〕+ main.py〔代码确认〕 |
| 前端技术 | Vue 3 (^3.5) + TypeScript 5.6 + Vite 6 + vue-tsc；单文件 App.vue（5 个 hash 路由模块） | frontend/package.json〔配置确认〕 |
| ⚠️ 文档矛盾 | docs/简历项目描述_STAR.md 写"TypeScript/React"，实际前端是 Vue 3，以代码为准 | 〔代码确认：package.json〕 |
| 数据库 | SQLite 单文件 data/job_agent.db | alembic.ini + config.py〔配置确认〕 |
| 数据库访问 | SQLAlchemy 2.x ORM（DeclarativeBase + mapped_column），Alembic 迁移（7 个版本），连接时 PRAGMA foreign_keys=ON；每次数据操作新建 Engine 并 dispose | app/models.py、app/sqlalchemy_db.py、app/database.py〔代码确认〕 |
| 大模型服务 | DeepSeek（deepseek-chat，默认 base_url https://api.deepseek.com），通过 openai SDK（OpenAI 兼容协议）调用；temperature=0.2；response_format=json_object；默认超时 30 秒；连接/超时错误自动重试 3 次 | app/services/llm_client.py〔代码确认〕 |
| Embedding 模型 | 当前项目未发现（无任何向量生成代码与配置） | 〔代码确认〕 |
| RAG/向量检索 | 本地关键词式检索：中文 2/3/4-gram + 英文 token + 停用词表，标题命中权重 ×3；简历按段落切片（约 1200 字符）；非向量检索 | app/services/knowledge_retriever.py〔代码确认〕 |
| Agent/Workflow 框架 | LangGraph (>=0.6,<1.0) StateGraph，单图 6 节点，条件路由；不拆多子 Agent | app/services/resume_graph.py〔代码确认〕 |
| OCR | rapidocr_onnxruntime（本地、延迟加载、单例缓存），Pillow + numpy 预处理 | app/services/ocr_service.py〔代码确认〕 |
| 文档解析 | pypdf（PDF）、python-docx（DOCX 含表格）、TXT/MD（utf-8-sig→gb18030 回退） | app/services/document_parser.py〔代码确认〕 |
| 简历导出 | python-docx（DOCX）、reportlab + STSong-Light CID 字体（单页 PDF）、Markdown 原文返回 | app/services/resume_export.py〔代码确认〕 |
| Chrome 自动化 | 自实现 CDP WebSocket 客户端（端口 9222，独立用户目录 data/chrome_profile，CHROME_EXECUTABLE 可配置） | app/services/chrome_automation.py〔代码确认〕 |
| 构建工具 | 前端 Vite（npm run build → frontend/dist）；后端无构建步骤 | vite.config.ts〔配置确认〕 |
| 测试工具 | 标准库 unittest（README 推荐命令），同时存在 pytest.ini 可用 pytest 运行；共 20 个测试文件、约 139 个 test 函数 | tests/〔测试确认〕 |
| 部署方式 | 本地 uvicorn（127.0.0.1:8000），前端构建后由 FastAPI 静态服务；无容器化/CI | main.py + README〔代码确认 + 文档确认〕 |
| 外部依赖服务 | ① DeepSeek API（唯一外部网络调用）② 本机 Chrome + BOSS 直聘页面（只读 DOM）③ 本地 OCR 模型（离线） | 〔代码确认〕 |