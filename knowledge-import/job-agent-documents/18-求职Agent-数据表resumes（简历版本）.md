适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
- 用途：审核后的简历正式版本（含 AI 改进版与人工保存版）
- 关键字段：id(PK)；owner_id；target_id(可空,FK SET NULL)；is_current(bool,默认false——某方向"当前生效简历")；version(int,必填,全局自增)；title/content(必填)；source_item_ids(Text,JSON,引用知识条目)；source_resume_id(int,可空,来源原稿)；greeting(Text)；job_snapshot(Text,JSON,含 job_id/job_title/company/jd_text)
- 关系：target_id FK；source_resume_id 逻辑指向 resume_sources；版本号全局（按 owner 最大 version+1）