适用系统：求职 Agent（FastAPI + Vue 3 本地求职工作台）。资料来源：2026-10-20 技术审计，标注〔代码确认〕〔配置确认〕〔测试确认〕〔文档确认〕〔待定〕。
## 重要定性
当前项目的 RAG 是**可解释的关键词式检索，不是向量/语义检索**；没有 Embedding、没有向量库。这是有意设计（README 与 docs 均明确：首版先验证可解释检索，效果不足再评估混合检索）〔代码确认 + 文档确认〕。

## RAG 真实调用链〔代码确认〕
POST /api/resume-runs → main.py：
1. `search_text = job_title + company + jd_text + instruction` 拼接
2. `retrieve_knowledge(全部知识条目, search_text)` → 最多 8 条结构化条目
3. `retrieve_resume_chunks(原稿全文, search_text)` → 最多 8 个简历切片
4. 结果（含编号、命中词）写入 input_snapshot → LangGraph generate_draft 节点 → resume_agent.build_user_prompt 将 `knowledge_base`（编号条目）与 `original_resume_retrieval`（编号切片）序列化为 JSON 用户提示 → DeepSeek
5. 模型返回的 evidence 只接受本次提供的 knowledge_item_id / resume_chunk_id（服务端二次校验非法编号直接丢弃）〔代码确认：resume_agent.py〕

## 文档如何创建、修改和删除
- 结构化知识条目：POST/PUT/DELETE /api/knowledge（全文存 knowledge_items 表，检索时实时计算，无预建索引）
- 简历原稿：POST /api/resume-sources（+upload）与 DELETE；切片在每次任务时即时生成，**切片不持久化**
- 删除资料即从后续检索语料中消失（无残留索引问题）〔代码确认〕

## 文档如何切片〔代码确认：retrieve_resume_chunks〕
- 先按空行分段；段落 ≤1200 字符整段成块；超长段落按中文句末标点/换行二次切分，硬截断上限 1200 字符
- 每块编号 chunk_id（从 1 起），保留原顺序

## Embedding 如何生成
- 当前项目未发现。无 Embedding 步骤。

## 向量如何保存
- 当前项目未发现。无向量存储。

## 检索时使用哪些查询内容
- 岗位名称 + 公司名 + JD 全文 + 用户改进指令的拼接文本〔代码确认：main.py search_text〕

## 相似度如何计算〔代码确认：knowledge_retriever.py〕
- 分词：英文/技术词正则（字母开头，可含 + # .）；中文连续段提取 2/3/4 字 n-gram；扣除停用词表（"负责/参与/熟悉/经验/岗位"等约 20 个）
- 知识条目得分 = 标题命中词条数 ×3 + 详情（分类+单位+时间+内容+来源）命中词条数；同分按原顺序
- 简历切片得分 = 命中词条数；同分按 chunk_id 升序

## 检索数量和过滤条件
- 两类检索均 limit=8（API 层与函数默认一致）〔代码确认〕
- 过滤：无评分阈值——有词重叠即入选；查询无有效词时知识条目返回前 8 条（按库顺序），简历切片全部标 general_context
- 简历切片无任何命中时的兜底：取前 3 片，match_type="fallback"，明确提示人工重点核对〔代码确认〕

## 检索结果如何交给大模型
- 以 JSON 内嵌于用户提示：`original_resume_retrieval`（chunk_id、内容、match_type、matched_terms）与 `knowledge_base`（条目 id、分类、标题、单位、时间、内容）〔代码确认：build_user_prompt〕
- System Prompt 约束：只能引用本次提供的编号；无证据进 missing_information；禁止补造数字

## 不同项目之间如何隔离
- 当前**不存在**多项目/多知识库隔离：单用户（owner_id="local"）单一全局知识库。简历改进任务以 AgentRun 为单位做任务级隔离（每次任务快照固定检索结果与编号）〔代码确认〕
- 岗位维度通过 job_snapshot 与任务快照实现"任务内隔离"，但检索语料是全库共享〔代码确认〕

## 空知识库时系统如何工作
- 完全可用：retrieve_knowledge 返回空列表；检索仍作用于简历切片；resume_agent 在无知识条目时以原稿切片（或整篇回退 full_source_fallback）生成草稿；evidence 用 resume_chunk_id 引用〔代码确认 + 文档确认：README"没有个人知识库也可以先用原始简历完成流程"〕

## 如何判断检索结果是否相关
- 机器侧：matched_terms（词面命中词）与 match_type（keyword/general_context/fallback）随结果展示并保存进快照〔代码确认〕
- 人工侧：前端把命中片段与来源编号展示给用户核对；模型证据引用限制在提供编号内；这是"审核辅助而非自动判断"〔文档确认〕

## 适合放入知识库的资料
- 教育/工作/项目经历（结构化：分类、标题、单位、时间段、内容、来源说明）
- 技能与成果条目（有事实依据、可核对的）
- 求职意向、个人信息（用于生成简历头部）

## 不适合放入知识库的资料
- API Key、密码、Cookie、Chrome 登录态（任何密钥与凭证）
- 简历图片二进制（本地文件保存，OCR 校对后的文字才入库）
- 临时性、无来源说明、无法核对的"愿望式"描述（会诱发模型编造风险）
- 他人隐私信息

## 推荐拆分的知识文档（导入故障排查 Agent 知识库时）
1. 项目概述与技术栈（文档 1）
2. 架构与模块（文档 2）
3. 核心业务流程（文档 3）
4. 数据模型（文档 4）
5. 故障排查手册（文档 5，条目级拆分最适合逐条建立检索）
6. 配置/启动/测试/部署（文档 6）
7. RAG 设计（文档 7）
8. 面试与决策（文档 8）
建议按"一个故障/一个流程/一张表"为粒度建立索引，便于命中。

## 当前 RAG 的已知限制〔代码确认〕
- 中文 n-gram 会产生子串巧合命中（如"数据库"命中"数据"），相关性与召回精度有限
- 无语义检索：同义不同词（"前端"vs"Web 开发"）无法命中
- 无评分阈值与去重；limit 固定 8
- 切片仅按段落，不识别简历章节语义
- 检索语料每次全量重算，条目量大时性能下降（个人规模无感知）

## 未来可以改进的地方〔文档确认的方向 + 待定评估〕
- 引入 Embedding + 向量检索做混合排序（README：检索效果不足时再评估）
- 增加同义词/技能词表扩充
- 评分阈值与结果多样性
- 持久化切片与增量索引