适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
- 用途：岗位记录与投递跟进
- 关键字段：id(PK)；owner_id；target_id(int,可空,FK SET NULL)；title(必填)；company/city/source/original_url(str,默认"")；description(Text)；status(str,默认"未处理"，取值：未处理/收藏/不合适/已投递/面试中/已结束——README 文档确认，代码为自由字符串)；notes(Text)；next_action(str)
- 关系：多对一 job_targets；resumes.job_snapshot 以 JSON 记录岗位快照（非外键）