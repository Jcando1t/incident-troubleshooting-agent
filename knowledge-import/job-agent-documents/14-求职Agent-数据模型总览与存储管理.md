适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
数据库：SQLite（data/job_agent.db，可用 DATABASE_PATH 覆盖）〔配置确认〕。所有表含 owner_id（当前恒为 "local"）。时间戳为字符串 CURRENT_TIMESTAMP〔代码确认：models.py〕。迁移版本共 7 个（20261013_0001 → 20261020_0007）〔代码确认：migrations/versions〕。


## 实体关系小结〔代码确认〕
job_targets 1—N jobs；job_targets 1—N resumes；resume_sources 1—N agent_runs；resumes.source_item_ids → knowledge_items（逻辑）；resumes.job_snapshot → jobs（逻辑快照）。

## 存储划分建议（当前实践归纳）
- 适合结构化数据库：求职方向、岗位、知识条目元数据、简历版本、任务状态与快照（当前已如此实现）
- 适合文档/RAG 语料：知识条目 content、简历原稿全文、简历切片（当前以 Text 字段 + 运行时切片实现，切片不持久化）
- 不应该放入 RAG：API Key 等密钥（不存在于任何表中）、Chrome 配置目录内容（登录态，含敏感会话）、数据库文件本身、简历图片二进制（仅本地文件）、owner_id 等权限字段

## 数据删除、更新和版本管理方式〔代码确认〕
- 删除：所有实体提供 DELETE 接口，物理删除；删除原稿/图片时同步删除本地文件（delete_private_file）；删除方向时岗位/简历的 target_id 置 NULL；删除原稿时其 agent_runs 级联删除
- 更新：直接覆盖 + updated_at 自动刷新；知识条目/岗位/方向支持编辑
- 版本管理：简历版本只增不改（version 自增），草稿阶段可反复编辑；旧版本不回滚；agent_runs 是任务级历史（含每次输入快照与质量检查）