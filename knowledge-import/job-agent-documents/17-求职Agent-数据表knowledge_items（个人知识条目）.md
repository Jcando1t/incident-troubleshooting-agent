适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
- 用途：结构化个人资料，作为 RAG 语料与事实依据
- 关键字段：id(PK)；owner_id；category(必填：个人信息/教育经历/工作经历/项目经验/技能/成果)；title(必填)；organization/period(str)；content(Text,必填)；source_note(str,来源说明)
- 关系：resumes.source_item_ids 以 JSON 数组记录引用（非外键）；agent_runs 快照记录 knowledge_item_ids