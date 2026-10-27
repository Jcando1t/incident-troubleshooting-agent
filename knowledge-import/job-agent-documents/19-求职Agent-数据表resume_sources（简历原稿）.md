适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
- 用途：粘贴/上传的原始简历文本与解析结果
- 关键字段：id(PK)；filename(str)；content(Text,解析出的文本)；source_image_ids(Text,JSON,关联 OCR 图片)；storage_path(str,可空,本地文件相对路径)
- 关系：agent_runs.source_resume_id FK 本表（ondelete=CASCADE）；source_image_ids 逻辑指向 resume_image_sources