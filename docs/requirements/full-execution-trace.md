# 全流程执行追踪需求

> 文档状态：草案  
> 创建日期：2026-06-21  
> 适用项目：`fund-agent-assistant`  
> 优先级：P0  
> 需求类型：Agent Harness 可靠性与资金安全

## 1. 业务背景

本项目服务于资金相关业务。Agent 可能查询付款信息、作出业务判断、调用外部工具，或发起会影响真实环境的操作。

在此类场景中，模型生成了回答或工具返回成功，都不能单独证明任务已经正确、安全地完成。系统必须能够回答：

- 谁在什么时间发起了任务？
- 本次任务使用了哪个 Agent、模型、Prompt、能力目录和工具版本？
- 模型获得了哪些必要且经过处理的上下文？
- 系统生成了什么计划，为什么选择某个能力或工具？
- 执行前进行了哪些权限、确认、风险和预算检查？
- 系统准备执行什么操作？
- 操作是否产生了真实副作用？
- 执行结果是成功、失败还是未知？
- 有哪些外部证据能够证明业务结果？
- 系统为什么重试、重新规划、追问用户、转人工或终止？
- 最终回答是否与所收集的证据一致？

当前系统已经通过应用日志、`NodeObservation`、`ToolCallRecord`、`DagRunResult`、编排事件和 Post 附件记录了部分执行信息，但这些信息彼此分散，无法形成统一、持久、严格有序且可审计的任务档案。

应用日志和会话附件不足以承担资金场景的审计职责，原因包括：

- 无法覆盖完整任务生命周期；
- 无法稳定表达事件之间的因果关系；
- 可能因日志轮转、重复输出或多请求混合而失真；
- 可能记录不必要的敏感信息；
- 无法可靠证明高风险操作在执行前已经通过授权；
- 无法区分确定失败与外部执行结果未知；
- 不是为只追加、不可篡改的审计场景设计的。

因此，项目需要建设持久化的全流程执行追踪，作为 Agent Harness 的事实与审计底座。

## 2. 需求定位

全流程 Trace 是可靠性体系的事实基础，但它不会替代：

- 身份认证与权限控制；
- 用户确认；
- 幂等控制；
- 执行预算；
- 业务验证；
- 恢复决策；
- 资金业务事实系统。

Trace 的作用是让这些机制可观察、可审计、可度量、可验证。

支付、交易、账户等业务系统仍然是余额、转账、付款状态和回单等真实业务状态的最终事实来源。Trace 记录 Agent 与 Harness 做了什么，但不能作为资金账本。

## 3. 当前目标

当前目标是为每一次 DAG 任务建立第一版可持久化的端到端 Trace 能力。

对于每一次用户任务，系统必须：

1. 在 Agent 开始执行前创建唯一的任务运行档案。
2. 为全流程分配稳定的 `episodeId` 和关联标识。
3. 持久化从请求接收到任务终态之间的重要事件。
4. 记录规划、校验、绑定、执行、验证和恢复之间的因果关系。
5. 在任何可能产生副作用的工具执行前，先持久化工具调用意图。
6. 明确记录外部执行结果是成功、失败还是未知。
7. 收集结构化 Evidence，并将其与业务结论关联。
8. 即使执行失败或异常退出，也必须持久化最终状态。
9. 通过数据最小化和脱敏保护密钥、个人信息及资金信息。
10. 能够通过 `episodeId` 重建完整且严格有序的执行时间线。

第一版应实现最小、同步、可验证的持久化闭环。当前阶段不要求引入消息队列、分布式追踪平台或通用事件平台。

## 4. 核心设计原则

### 4.1 副作用执行前必须先持久化

对于可能产生资金或其他外部副作用的操作，必须遵循以下顺序：

```text
持久化工具调用意图
→ 检查权限和执行策略
→ 检查是否获得必要的用户确认
→ 生成或取得幂等键
→ 执行工具
→ 持久化执行结果
→ 必要时反查真实业务状态
→ 收集 Evidence
→ 验证任务目标
```

如果工具调用意图无法持久化，则不得执行具有副作用的工具。

### 4.2 事件只允许追加

执行事实必须以只追加事件的方式记录。

不得通过修改已有事件改变历史。纠正、恢复决策和后续观察结果必须以新事件的形式追加。

Episode 的汇总字段可以更新，以便检索和统计；但只追加事件链才是权威执行时间线。

### 4.3 失败同样必须可追踪

当业务事务或执行步骤失败时，Trace 不得随业务事务一起消失。

关键 Trace 写入应使用独立于业务操作的事务边界。业务事务回滚时，解释其失败原因的 Trace 记录不得被回滚。

### 4.4 执行结果未知是一等状态

网络超时或响应丢失不能证明外部操作失败。

当真实结果无法确定时，系统必须：

- 记录 `RESULT_UNKNOWN`；
- 不得盲目重试非幂等操作；
- 反查真实环境状态；
- 收集 Evidence；
- 根据证据决定完成、恢复、转人工或终止。

### 4.5 证据优先于 Agent 自述

Agent 的回答或工具的成功响应，不能单独证明真实业务结果。

Evidence 的可靠性优先级为：

1. 确定性程序检查；
2. 对真实环境进行写后反查；
3. 业务规则；
4. LLM 语义判断；
5. 人工审核。

### 4.6 敏感数据最小化

Trace 只允许持久化审计、验证、恢复和评测所必需的信息。

密钥不得进入 Trace。敏感信息和资金信息必须根据数据分类进行掩码、哈希、加密、摘要或直接省略。

### 4.7 不记录完整思维链

Trace 可以保存结构化决策摘要、原因码、候选项选择结果和因果关系，但不得保存模型的完整私有思维链。

## 5. 需求范围

### 5.1 本期范围

第一阶段包括：

- `AgentEpisode` 生命周期；
- 只追加的 `TraceEvent`；
- 稳定的全流程关联标识；
- Agent 版本快照；
- 规划和计划校验事件；
- 能力与工具绑定事件；
- 节点调度和执行事件；
- 模型调用元数据；
- 工具调用意图和结果事件；
- 适用时记录执行策略和用户确认决策；
- 节点验证和目标验证事件；
- Evidence 记录；
- Replan 和恢复事件；
- Episode 最终状态；
- 总耗时和执行次数统计；
- 同步持久化 Trace；
- Trace 数据脱敏；
- Trace 完整性校验；
- 根据 `episodeId` 查询完整时间线；
- 异常退出检测及终态补偿。

### 5.2 本期不包含

第一阶段不包括：

- 替代支付或资金系统成为业务事实来源；
- Kafka 或其他事件流平台；
- 通用企业事件平台；
- 大规模分布式 DAG 追踪；
- 自动重试非幂等资金操作；
- Trace 可视化大屏；
- 无限制保存完整 Prompt、记忆或工具响应；
- 使用 LLM 判断作为资金业务结果的唯一证据。

## 6. 核心领域对象

### 6.1 AgentEpisode

`AgentEpisode` 代表一次完整的用户任务。

领域层通过 `episodeCode` 标识 Episode；数据库使用自增 `id` 作为内部主键和外键关联。数据库内部主键不得暴露给模型或外部系统。

建议字段：

```text
episodeCode
requestId
conversationId
userIdReference
agentVersion
originalGoalRedacted
riskLevel
status
nextSequenceNo
lastEventHash
eventCount
startedAt
finishedAt
elapsedMs
stepCount
modelCallCount
toolCallCount
tokenUsage
finalErrorCode
finalFailureStage
sealed
rowVersion
createdAt
updatedAt
createdBy
updatedBy
```

建议生命周期：

```text
CREATED
→ RUNNING
→ WAITING_USER
→ WAITING_CONFIRMATION
→ COMPLETED
→ FAILED
→ ABORTED
→ RESULT_UNKNOWN
```

任何已经停止执行的 Episode 最终都必须进入明确的终态或等待状态，不得无限期停留在 `RUNNING`。

### 6.2 TraceEvent

`TraceEvent` 代表一条不可变的执行事实。

领域层通过 `eventCode` 引用事件；数据库使用自增 `id` 建立 Episode、父事件和直接原因事件的外键关系。

建议字段：

```text
eventCode
episodeCode
sequenceNo
parentEventCode
causationEventCode
correlationId
eventType
stage
nodeId
capability
toolName
status
reasonCode
summary
payloadJson
payloadSchemaVersion
payloadHash
producerId
occurredAt
receivedAt
persistedAt
previousHash
eventHash
signatureAlgorithm
signingKeyId
eventSignature
createdBy
```

建议数据库约束和索引：

```text
UNIQUE(episodeId, sequenceNo)
INDEX(episodeId, occurredAt)
INDEX(eventType, occurredAt)
INDEX(reasonCode, occurredAt)
```

`sequenceNo` 定义 Episode 内的严格顺序，`parentEventCode` 和 `causationEventCode` 记录父子及直接因果关系，`previousHash` 和 `eventHash` 用于提供事件链篡改检测能力。

对于一个事件由多个前置事件共同导致的情况，使用 `TraceEventRelation` 表达 `CAUSED_BY`、`DEPENDS_ON`、`RETRY_OF`、`REPLAN_OF`、`VERIFIES`、`PRODUCED_EVIDENCE` 和 `RECOVERS_FROM` 等关系。

### 6.3 Evidence

Evidence 是 Verifier 用来支持或否定业务结论的一等数据对象。

建议字段：

```text
evidenceId
episodeId
eventId
nodeId
sourceType
sourceReference
claim
expectedValueRedacted
actualValueRedacted
reliabilityLevel
verificationStatus
collectedAt
payloadHash
```

示例：

```text
claim: payment_status
actualValue: PAID
sourceType: DATABASE_QUERY
reliabilityLevel: DETERMINISTIC
```

## 7. 必需的 Trace 事件

第一阶段至少支持：

```text
EPISODE_CREATED
EPISODE_STARTED
CONTEXT_ASSEMBLED

PLAN_REQUESTED
PLAN_GENERATED
PLAN_VALIDATED
TOOL_BOUND

NODE_READY
NODE_STARTED
NODE_COMPLETED
NODE_FAILED

MODEL_CALL_STARTED
MODEL_CALL_COMPLETED
MODEL_CALL_FAILED

POLICY_CHECKED
CONFIRMATION_REQUESTED
CONFIRMATION_RECEIVED

TOOL_CALL_INTENT_RECORDED
TOOL_CALL_STARTED
TOOL_CALL_SUCCEEDED
TOOL_CALL_FAILED
TOOL_CALL_RESULT_UNKNOWN

EVIDENCE_COLLECTED
NODE_VERIFIED
GOAL_VERIFIED

RECOVERY_DECIDED
RETRY_SCHEDULED
REPLAN_GENERATED

EPISODE_WAITING_USER
EPISODE_WAITING_CONFIRMATION
EPISODE_COMPLETED
EPISODE_FAILED
EPISODE_ABORTED
EPISODE_RESULT_UNKNOWN
```

## 8. 持久化与事务要求

### 8.1 必须持久化

每次 DAG 执行都必须创建并持久化 Episode。生产执行不接受仅保存在内存中的 Trace。

### 8.2 独立事务边界

关键 Trace 事件必须独立于业务操作持久化，例如使用 `REQUIRES_NEW` 事务传播方式。

需要保证：

- 业务回滚不会删除对应的失败记录；
- 节点异常不会删除此前已经持久化的执行事实；
- 即使副作用执行失败，调用意图仍然持久存在。

### 8.3 第一阶段采用同步写入

第一阶段应同步持久化关键事件，持久化成功后流程才能继续。

只有在能够继续保证持久性的前提下，后续才可以引入异步机制，例如事务 Outbox。资金操作不接受尽力而为的异步日志。

### 8.4 Trace 持久化失败处理

| 失败场景 | 必须采取的行为 |
|---|---|
| Episode 创建失败 | 不得开始 Agent 执行 |
| 关键事件持久化失败 | 安全终止执行 |
| 副作用工具意图持久化失败 | 不得调用工具 |
| 工具成功但结果事件持久化失败 | 发出高风险告警并反查真实业务状态 |
| Episode 终态更新失败 | 触发确定性补偿，不得报告任务已可靠完成 |
| Trace 存储不可用 | 熔断所有具有副作用的能力 |

## 9. 敏感数据与安全要求

Trace 字段应采用以下数据分类：

```text
PUBLIC
INTERNAL
SENSITIVE
SECRET
FINANCIAL
```

处理要求：

- `PUBLIC`：可以正常保存；
- `INTERNAL`：保存但限制访问；
- `SENSITIVE`：必须掩码、哈希、加密或摘要；
- `SECRET`：禁止保存；
- `FINANCIAL`：最小化保存，必要时加密，并对访问行为进行审计。

Trace 中禁止出现：

- API Key、Access Token、密码或其他私密凭据；
- 完整银行卡号或证件号码；
- 非必要的完整用户身份信息；
- 回单临时下载凭据；
- 完整模型私有推理过程；
- 无限制的完整长期记忆；
- 对验证没有必要的原始请求或响应内容。

模型调用 Trace 通常只保留：

- 模型及供应商版本；
- Prompt 模板版本；
- 脱敏后的输入摘要或输入哈希；
- 结构化输出；
- 安全情况下的供应商请求 ID；
- Token 用量；
- 调用耗时；
- 完成或失败状态。

工具调用 Trace 通常只保留：

- 工具及 Provider 版本；
- 脱敏后的参数；
- 幂等键或其安全引用；
- 权限和确认决策；
- 脱敏后的结果摘要；
- 外部请求或业务交易引用；
- 调用耗时；
- 结果确定性；
- 错误分类。

## 10. 可靠性与完整性要求

系统必须：

- 维护 Episode 内严格的事件顺序；
- 防止产生重复的 sequence number；
- 尽可能保证重复事件提交的幂等性；
- 通过完整性检查发现事件缺失或被修改；
- 分别记录事件发生时间和持久化时间；
- 保留原始外部请求引用；
- 区分执行失败与 Trace 持久化失败；
- 区分确定失败与结果未知；
- 支持查询某个 Episode 的全部事件和 Evidence；
- 防止未经授权读取资金 Trace 数据；
- 在后续安全里程碑中审计敏感 Trace 的访问行为。

### 10.1 第一阶段领域模型规则

当前领域模型采用不可变对象：

- `AgentEpisode.append(...)` 不修改原 Episode，而是返回新的 Episode、受保护的 `TraceEvent`、新的 `TraceContext` 和因果关系。
- `AgentTrace.append(...)` 将上述结果合并为新的完整 Trace 聚合，原聚合保持不变。
- `AgentTrace.appendEvidence(...)` 对 Evidence 内容进行规范化并计算完整性 Hash，然后返回新的聚合。
- `AgentTrace.seal(...)` 只允许对 `COMPLETED`、`FAILED`、`ABORTED` 状态封印。
- `RESULT_UNKNOWN` 不是最终状态，不允许封印，可以在真实状态反查后恢复为运行态或进入明确终态。

事件保护规则：

```text
原始 JSON
→ Canonical JSON
→ SHA-256 payloadHash
→ previousHash + 事件安全字段
→ SHA-256 eventHash
→ HMAC-SHA256 eventSignature
```

HMAC 密钥不得进入领域对象、Trace、日志或数据库。当前实现要求密钥至少为 32 字节，后续由运行配置或密钥管理服务提供。

本阶段的 Hash 和 HMAC 用于完整性与真实性校验，不属于业务数据的可逆加密。需要保密的资金字段仍需在进入 Trace 前完成脱敏或使用独立的数据加密机制。

### 10.2 持久化幂等与原子性规则

`PersistentTraceStore` 是当前 Trace 的唯一持久化入口。写操作采用独立事务，并遵循以下规则：

```text
createEpisode:
按 requestId + episodeCode 检查幂等
→ 幂等 Upsert Episode
→ 锁定 Episode
→ 追加并持久化 EPISODE_CREATED
→ 更新 Episode 投影

append:
锁定 Episode
→ 按 eventCode 检查幂等
→ 校验父事件和原因事件属于当前 Episode
→ 调用 AgentEpisode.append
→ 插入 TraceEvent
→ 插入 TraceEventRelation
→ 使用 rowVersion 更新 Episode 投影

appendEvidence:
锁定 Episode
→ 按 evidenceCode 检查幂等
→ 校验关联 Event
→ 生成 Evidence Hash
→ 插入 Evidence

sealEpisode:
锁定 Episode
→ 校验完整事件链
→ 生成并验证 Seal
→ 插入 EpisodeSeal
→ 更新 Episode sealed 状态
```

代码层幂等规则：

- 相同幂等标识和相同内容重复提交时，返回已持久化结果，不重复插入。
- 相同幂等标识但内容不同，抛出 `TraceIdempotencyConflictException`。
- `requestId`、`episodeCode`、`eventCode`、`evidenceCode` 和数据库唯一索引共同提供最终约束。
- 同一 Episode 的写入使用 `SELECT ... FOR UPDATE` 串行化，防止 sequence number 和 Hash 链分叉。
- Episode 投影更新同时校验 `rowVersion`，受影响行数不是 1 时整个事务失败。
- Event、Relation 和 Episode 投影必须在同一事务中提交或回滚。

Trace 持久化默认不启用。启用时必须提供：

```text
AGENT_TRACE_ENABLED=true
AGENT_TRACE_SIGNING_KEY_ID=<密钥版本标识>
AGENT_TRACE_SIGNING_KEY_BASE64=<至少32字节密钥的Base64编码>
```

签名密钥没有代码或配置文件默认值，不得写入数据库、日志或代码仓库。

### 10.3 真实 MySQL 集成测试

真实数据库验证类：

```text
fund-agent-repo/src/test/java/com/fundagent/repo/trace/
PersistentTraceStoreMySqlIntegrationTest.java
```

默认执行 `mvn test` 时该测试会被跳过。只有显式设置以下环境变量时才连接 MySQL：

```text
TRACE_MYSQL_IT=true
TRACE_IT_DB_URL=jdbc:mysql://<host>:<port>/<database>?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
TRACE_IT_DB_USERNAME=<username>
TRACE_IT_DB_PASSWORD=<password>
```

运行命令：

```bash
mvn -q -pl fund-agent-repo -am \
  -Dtest=PersistentTraceStoreMySqlIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

测试覆盖：

- Episode 与 `EPISODE_CREATED` 只创建一次；
- 创建请求和事件追加的内容级幂等；
- 相同幂等键、不同内容的冲突拒绝；
- 两线程并发追加时 sequence 连续且 Hash 链不分叉；
- 外层业务事务回滚后，`REQUIRES_NEW` Trace 仍然保留；
- Relation 持久化故障时，Event 插入和 Episode 投影整体回滚；
- 持久化后的完整 Trace 能够通过完整性校验。

测试仅操作随机生成的 `IT_` 前缀 Trace 数据，并在每个测试结束后按外键顺序清理。目标账号需要对五张 Trace 表具备测试所需的 `SELECT`、`INSERT`、`UPDATE` 和 `DELETE` 权限。该测试必须指向测试数据库，禁止对生产数据库执行。

## 11. 验收标准

第一阶段只有在以下条件全部满足时才算完成：

1. 每次 DAG 执行都在规划或工具执行前创建唯一且已持久化的 `episodeId`。
2. Episode 创建失败时，Agent 不得开始执行。
3. 每条持久化事件都包含 Episode ID 和唯一、严格递增的 sequence number。
4. 能够通过 `episodeId` 重建完整执行时间线。
5. 时间线覆盖规划、计划校验、工具绑定、节点执行、模型调用、工具调用、验证、恢复和最终状态。
6. 每个节点都能追踪到其输入来源、执行器、执行结果和完成性检查结果。
7. 每次工具调用都必须在实际调用前持久化调用意图事件。
8. 具有副作用的工具意图持久化失败时，实际工具调用次数必须为零。
9. 每个工具结果都被明确分类为成功、失败或结果未知。
10. 非幂等操作发生超时或响应丢失时，必须记录 `RESULT_UNKNOWN`，且不得盲目重试。
11. 相关业务结论能够关联到结构化 Evidence。
12. 目标验证能够记录哪些 Evidence 支持或否定任务完成。
13. Episode 最终状态与最后一条生命周期事件保持一致。
14. DAG 执行发生异常时，异常前已持久化的 Trace 事件不得丢失。
15. 业务事务回滚不得回滚关键 Trace 事件。
16. 能够发现异常遗留在 `RUNNING` 状态的 Episode，并将其补偿到明确状态。
17. 修改或删除 Episode 中间事件后，Trace 完整性检查能够发现异常。
18. Trace 持久化失败时，具有副作用的能力必须采用失败关闭策略。
19. 持久化 Trace 中不得出现 API Key、密码、Access Token 或其他配置密钥。
20. 用户敏感信息和资金字段必须按照数据分类策略处理。
21. Trace 不得持久化完整私有思维链。
22. Episode 至少能够统计总耗时、步骤数、模型调用次数和工具调用次数。
23. 自动化测试必须覆盖成功执行、节点失败、持久化失败、外部结果未知、重复事件提交、事件顺序和敏感数据脱敏。
24. 相关回归测试必须通过，且未经明确批准不得改变现有用户行为或业务行为。

## 12. 验证计划

实现阶段至少需要：

- Episode 生命周期和事件顺序单元测试；
- Trace 脱敏单元测试；
- 哈希事件链完整性单元测试；
- 独立事务行为的 Repository 集成测试；
- 使用确定性 Fake Model 和 Fake Tool 的 Runtime 测试；
- Trace 在副作用工具执行前写入失败的故障注入测试；
- 工具执行结果未知的故障注入测试；
- 多条事件持久化后执行抛出异常的故障注入测试；
- 验证禁止保存的密钥不会进入存储内容；
- 完整 DAG 执行的时间线重建测试。

仅通过编译不能作为需求完成的证明。

## 13. 风险与约束

### 13.1 主要风险

- 保存了过多敏感或资金信息；
- 错误地将 Trace 作为资金事实系统；
- 副作用意图尚未持久化就执行真实操作；
- Trace 与业务事务一起回滚，导致失败记录丢失；
- 外部执行结果未知时盲目重试；
- 数据库写入量过高；
- 并发执行时产生事件顺序冲突；
- 终态 Trace 写入失败后仍对外宣称任务成功。

### 13.2 约束

- 资金安全优先于可用性和执行速度；
- 第一阶段优先保证同步持久化，而不是吞吐量；
- 不得仅因 Trace 显示超时就重试非幂等操作；
- 持久化 Trace 的访问必须遵循最小权限原则；
- 除非另行批准，实现不得改变现有 DAG 和用户行为。

## 14. 建议模块边界

建议的架构方向为：

```text
fund-agent-core
└── trace
    ├── TraceStore
    ├── TraceEvent
    ├── TraceEventType
    ├── TraceEventRelation
    ├── TraceContext
    ├── AgentEpisode
    ├── EpisodeStatus
    ├── Evidence
    ├── EpisodeSeal
    ├── AppendTraceEventCommand
    ├── AppendEvidenceCommand
    └── TraceIntegrityResult

fund-agent-repo
└── trace 持久化
    ├── AgentEpisodeEntity / Mapper
    ├── TraceEventEntity / Mapper
    ├── TraceEventRelationEntity / Mapper
    ├── TraceEvidenceEntity / Mapper
    └── EpisodeSealEntity / Mapper
```

后续的 Trace 记录服务应依赖 `TraceStore` 存储契约。Planner、Runtime、NodeExecutor、工具执行、Verifier 和 Recovery 组件不得直接依赖数据库 Mapper。

当前阶段已经完成 Trace 领域模型、状态枚举、存储契约、数据库实体、Mapper、哈希签名、独立事务持久化，以及新 DAG 主链路的第一版接入。

## 15. LLM 调用监管协议

生产新 DAG 的 LLM 调用采用供应商无关协议：

```text
CapabilityDagPlanner / CapabilityDagRePlanner
ToolNodeExecutor / ReasonNodeExecutor / AnswerNodeExecutor
                    ↓
             AgentLLMService
                    ↓
          TraceableLLMService
                    ↓ delegate
          AgentLLMService Provider
                    ↓
       OpenAIService / 其他模型实现
```

`TraceableLLMService` 只依赖 `AgentLLMService` 接口，不依赖 `OpenAIService`。切换模型供应商时，只需要替换底层 Provider 实现。

### 15.1 LLMRequest

`LLMRequest` 统一承载：

```text
traceContext
callerType
callerName
nodeId
capability
systemPrompt
history
currentMessage
responseFormat
metadata
```

`history` 和 `metadata` 使用不可变副本，调用方后续修改原集合不会影响已经创建的请求。

### 15.2 LLMResponse

`LLMResponse` 统一承载：

```text
content
provider
model
providerRequestId
promptTokens
completionTokens
totalTokens
finishReason
elapsedMs
traceContext
```

OpenAI 兼容 Provider 从响应中提取模型、Request ID、Token 和 finish reason。装饰器返回完成事件后的新 `TraceContext`，后续调用必须继续传递该上下文。

### 15.3 TraceableLLMService 行为

```text
持久化 MODEL_CALL_STARTED
→ 调用 AgentLLMService Provider
→ 成功：持久化 MODEL_CALL_COMPLETED
→ 失败：持久化 MODEL_CALL_FAILED
```

安全要求：

- `MODEL_CALL_STARTED` 持久化失败时，不得调用模型。
- 模型成功但 `MODEL_CALL_COMPLETED` 持久化失败时，不得向上层返回可靠成功。
- 模型失败时必须先记录失败事件，再抛出原模型异常。
- Prompt、历史消息、当前消息和模型原始输出不得直接进入 Trace Payload。
- Trace 只保存 `promptHash`、`schemaHash`、`outputHash`、调用者、模型元数据、Token、耗时和错误分类。

### 15.4 监管范围

本期纳入：

```text
CapabilityDagPlanner
CapabilityDagRePlanner
ToolNodeExecutor
ReasonNodeExecutor
AnswerNodeExecutor
```

本期不纳入：

```text
ConversationSummaryService
PromptLabService
GraphTaskPlanner
GraphAnswerGenerator
PlannerAgent
```

`OpenAIService` 同时保留旧 `LLMService` 接口，供不纳入本期监管的系统调用和旧链路使用；新 DAG 主链路通过 `agentLLMService` Bean 接入。该 Bean 在 `agent.trace.enabled=true` 时优先使用 `traceableAgentLLMService`，否则回退到底层 raw Provider。

## 16. 新 DAG 主链路接入状态

新 DAG 主链路的第一版 Trace 闭环如下：

```text
ChatService
  -> TraceStore.createEpisode
  -> EPISODE_STARTED
  -> CapabilityDagPlanner.plan(..., TraceContext)
  -> DagRuntime / ReplanningDagRuntime
  -> ToolNodeExecutor / ReasonNodeExecutor / AnswerNodeExecutor
  -> CapabilityDagRePlanner
  -> EPISODE_COMPLETED / EPISODE_FAILED / EPISODE_WAITING_USER
```

### 16.1 Episode 创建点

`ChatService` 是用户主流程 Episode 的创建点。主流程在进入 Planner 之前先创建 `AgentEpisode`，并追加 `EPISODE_STARTED`。如果 Trace 持久化失败，主流程不会继续调用 Planner 或模型。

主链路必须 fail-closed：如果 `TraceStore` 未启用或未注入，`ChatService` 必须拒绝执行用户 DAG 主流程，不能回退成无 Trace 执行。`agent.trace.enabled=false` 只允许旧调试入口、后台任务或尚未纳入本期监管的链路继续使用 raw LLM，不适用于用户主流程。

### 16.2 TraceContext 传播

`DagExecutionContext` 承载当前 `TraceContext`。每次 Trace append 后，调用方必须使用返回的新 `TraceContext` 继续后续调用。

已接入传播点：

- `CapabilityDagPlanner`
- `DagRuntime`
- `ReplanningDagRuntime`
- `CapabilityDagRePlanner`
- `ToolNodeExecutor`
- `ReasonNodeExecutor`
- `AnswerNodeExecutor`

### 16.3 规划与绑定语义事件

主链路不仅记录 LLM 调用，还必须记录系统接受后的规划语义：

- `PLAN_REQUESTED`：开始请求能力 DAG 规划；
- `PLAN_GENERATED`：Planner 输出已被系统解析为 DAG Plan；
- `PLAN_VALIDATED`：DAG Plan 通过协议和 capability 校验；
- `PLAN_REJECTED`：DAG Plan 被系统校验拒绝；
- `TOOL_BINDING_STARTED`：开始将 capability 节点绑定到工具；
- `TOOL_BOUND`：工具绑定成功；
- `TOOL_BINDING_FAILED`：工具绑定失败。

`PLAN_GENERATED` 的 Payload 必须包含 `dagId`、`goalHash`、`nodeCount`、`capabilities` 和节点摘要。节点摘要记录 `nodeId`、`nodeType`、`capability`、`dependsOn`、`expectedOutputs` 和 `instructionHash`，不得记录原始用户消息、完整 Prompt、完整模型输出或未脱敏 instruction。

这样 Trace 可以回答：

```text
Planner 生成了哪些节点？
系统是否接受了这个计划？
为什么后续 Runtime 会执行这些节点？
能力节点最终绑定到了哪些工具？
```

### 16.4 节点事件

`DagRuntime` 在节点执行前追加 `NODE_STARTED`，在节点执行和节点完成检查之后追加以下之一：

- `NODE_COMPLETED`
- `NODE_FAILED`
- `NODE_SKIPPED`
- `NODE_WAITING_USER`

节点事件只记录节点 ID、能力、节点类型、执行状态、完成检查结果和错误码等结构化元数据，不写入原始用户消息、Prompt 或模型输出。

### 16.5 LLM 调用事件

主链路中带 `TraceContext` 的 LLM 调用统一走 `AgentLLMService`，由 `TraceableLLMService` 追加：

- `MODEL_CALL_STARTED`
- `MODEL_CALL_COMPLETED`
- `MODEL_CALL_FAILED`

无 `TraceContext` 的调试入口和旧链路仍可回退到旧 `LLMService`，避免把本期未纳入监管的后台行为误挂到用户 Episode 下。

### 16.6 工具调用事件

`ToolNodeExecutor` 在真实工具执行前后追加：

- `TOOL_CALL_STARTED`
- `TOOL_CALL_SUCCEEDED`
- `TOOL_CALL_FAILED`
- `TOOL_CALL_RESULT_UNKNOWN`

工具 Trace 记录工具名、节点 ID、能力、Provider、风险等级、参数 Hash、成功状态和错误码。不得写入原始工具参数或原始工具结果。

### 16.7 当前边界

本期主链路闭环不包含：

- 后台 Conversation Summary 的独立系统 Episode；
- Policy / Confirmation / Evidence 的业务级接入；
- Episode 自动 Seal。

这些能力需要在后续阶段按资金安全要求继续接入。
