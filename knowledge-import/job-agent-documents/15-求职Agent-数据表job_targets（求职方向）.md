适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
- 用途：管理多个求职方向及其搜索关键词、城市
- 关键字段：id(int,PK)；owner_id(str,必填,默认"local")；title(str,必填)；keywords(str,默认"")；cities(Text,必填,JSON 数组字符串)；is_active(int,默认1)；created_at/updated_at(str)
- 字段含义：title=方向名；keywords=搜索词；cities=意向城市列表；is_active=启用/暂停
- 关系：jobs.target_id、resumes.target_id 外键指向本表（ondelete=SET NULL）