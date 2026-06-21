# Fund Agent Harness 建设目标

> 文档状态：目标草案  
> 制定日期：2026-06-21  
> 适用项目：`fund-agent-assistant`

## 1. 文档目的

本文档用于统一项目下一阶段的建设方向。

项目不再以“增加更多 Agent、Prompt、工具或编排层”为首要目标，而是以建立一套可验证、可控制、可追踪、可恢复、可评测和可灰度发布的 Agent Harness 为主线。

Harness 不替代模型、Planner、DAG、Memory、RAG 或 MCP，而是把这些能力组织进一个可靠的运行和迭代体系中。

## 2. 核心定义

本项目对 Agent Harness 的定义是：

> Agent Harness 是位于模型与真实环境之间的运行控制系统。它负责向模型提供合适的上下文和工具，约束模型提出的行动，记录执行过程，收集完成证据，判断目标是否达成，并在失败后选择安全的恢复方式。

目标系统可以概括为：

```text
Agent = Model + Harness + Environment

Harness =
  Context
  + Capability / Tool
  + Runtime / State
  + Policy / Budget
  + Evidence / Verification
  + Recovery
  + Trace
  + Evaluation / Release
```

模型负责提出判断和行动建议，Harness 负责决定这些行动能否执行、如何执行以及如何证明执行结果。

## 3. 为什么需要转向 Harness

传统生成式 Agent 常使用如下结束条件：

```text
模型生成回答
或
工具返回成功
或
执行计划中的节点全部结束
```

这些条件不能证明用户的真实目标已经完成。例如：

- 模型说“回单已发送”，但系统中没有发送记录。
- 发送接口返回成功，但因重试产生了重复发送。
- 工具调用没有报错，但查询到的不是用户指定的付款单。
- DAG 全部执行结束，但遗漏了用户目标中的条件分支。
- 新版本回答更流畅，却提高了越权调用和业务风险。

因此，项目必须从“Agent 能否执行”继续演进为：

```text
Agent 是否完成了正确的任务
是否有外部证据证明完成
执行过程是否符合权限和安全约束
失败是否能够被识别、解释和恢复
新版本是否经过评测并优于旧版本
```

## 4. 当前项目基础

项目已经具备 Harness 的部分基础：

- `CapabilityCatalog`：约束 Planner 可以使用的业务能力。
- `ToolSelector / ToolBinder / ToolRegistry`：选择、绑定和分发工具。
- `LocalToolProvider / MCP Tool Provider`：接入内部能力和外部工具。
- `DagRuntime`：执行能力节点并维护图状态。
- `NodeCompletionChecker`：执行节点后的基础检查。
- `FinalDagVerifier`：整图执行后的基础检查。
- `RePlanner`：根据执行结果补充后续任务。
- `MemoryAssembler`：按使用场景组装上下文。
- `Observation`：保存节点和工具执行结果。

当前系统的主要缺口不是缺少新的 Agent 类型，而是缺少完整的可靠性闭环：

- 缺少统一的任务运行档案和结构化 Trace。
- 缺少以业务目标为中心的成功条件。
- 缺少可追溯的结构化 Evidence。
- 当前 Verifier 以结构检查为主，业务完成性验证不足。
- 缺少统一的超时、步数、Token、成本和工具调用预算。
- 缺少幂等、副作用、权限和人工确认策略。
- 缺少按失败类型进行恢复决策的统一机制。
- 缺少固定场景评测、版本对比、灰度和回滚体系。

## 5. 总体建设目标

项目目标是建设一个资金业务 Agent Harness，使不同模型、Prompt、工具和编排策略能够在统一的可靠性体系下运行。

目标运行闭环：

```text
User Goal
   ↓
Context Assembly
   ↓
Plan / Decide
   ↓
Policy Check
   ↓
Execute
   ↓
Observation + Evidence
   ↓
Verify
   ├── PASS → Finish
   ├── RETRY → Retry Safely
   ├── REPLAN → Change Execution Path
   ├── ASK_USER → Request Missing Information
   ├── HUMAN_REVIEW → Wait for Approval
   └── ABORT → Stop Safely
```

目标迭代闭环：

```text
Production Trace / Failure
   ↓
Add Evaluation Scenario
   ↓
Change Model / Prompt / Harness
   ↓
Offline Evaluation
   ↓
Shadow
   ↓
Canary
   ↓
Full Release or Rollback
```

## 6. 核心设计原则

### 6.1 以用户目标完成为结束条件

不能以“模型已回答”“工具调用成功”或“DAG 节点运行结束”作为唯一完成依据。

每个任务都应具有明确的 `SuccessCriteria`，最终由 Verifier 根据执行证据判断目标是否达成。

### 6.2 证据优先于模型自述

涉及真实世界状态的结论，应优先使用以下证据：

1. 确定性程序检查。
2. 真实环境反查。
3. 业务规则。
4. LLM 语义评判。
5. 人工审核。

LLM 可以协助判断语义完整性，但不能单独证明付款、发送、文件生成、权限或部署等客观状态。

### 6.3 重试必须基于失败分类

重试不是默认动作。Harness 必须先判断：

- 操作是否可重试。
- 操作是否幂等。
- 是否已经产生副作用。
- 是否需要修改参数。
- 是否应重新规划。
- 是否缺少用户信息。
- 是否必须终止或转人工。

### 6.4 模型提出行动，Harness 决定是否执行

权限、确认、预算、敏感数据和副作用约束必须由确定性代码执行，不能只依赖 Prompt。

### 6.5 每次运行必须可追溯

系统应能够回答：

- 哪个 Agent 版本处理了请求？
- 模型看到了哪些上下文？
- 为什么选择某个能力和工具？
- 工具使用了什么参数？
- Policy 为什么允许或拒绝执行？
- 任务凭什么被判定完成？
- 为什么发生重试、重规划或终止？

### 6.6 每次发布必须有评测证据

模型、Prompt、知识库、工具 Schema、Planner、Verifier 或执行策略的变化都可能改变 Agent 行为。

所有行为变化都必须经过固定场景回归和版本对比，不能只依赖人工体验。

### 6.7 DAG 是实现手段，不是最终目标

DAG、ReAct、单 Agent、子 Agent 和 Workflow 都是可选择的执行策略。

项目不以编排复杂度衡量成熟度，而以任务成功率、安全性、可恢复性、可解释性和运行成本衡量成熟度。

## 7. 目标架构

```text
                         Agent Harness

 Request
    │
    ▼
 Agent Version ── Context Builder ── Capability Planner
    │                                      │
    │                                      ▼
    │                              Execution Policy
    │                         权限 / 确认 / 预算 / 风险
    │                                      │
    │                                      ▼
    │                              Runtime / Scheduler
    │                                      │
    │                    ┌─────────────────┴─────────────────┐
    │                    ▼                                   ▼
    │             Local Tool Provider                 MCP Tool Provider
    │                    │                                   │
    │                    └─────────────────┬─────────────────┘
    │                                      ▼
    │                         Observation + Evidence
    │                                      │
    │                                      ▼
    │                         Node / Goal Verifier
    │                                      │
    │               ┌──────────────────────┼──────────────────────┐
    │               ▼                      ▼                      ▼
    │             Finish                Recovery              Human Review
    │                              Retry / Replan / Ask
    │
    └──────────────────────────→ Agent Episode / Trace
                                             │
                                             ▼
                                  Evaluation / Release
                              回归 / Shadow / 灰度 / 回滚
```

## 8. 核心建设模块

### 8.1 Agent Version

每次运行绑定一个完整版本，而不只是模型名称：

```text
AgentVersion
├── modelVersion
├── promptVersion
├── capabilityCatalogVersion
├── toolSchemaVersion
├── knowledgeBaseVersion
├── contextPolicyVersion
├── plannerVersion
├── executionPolicyVersion
└── verifierVersion
```

版本信息必须进入 Trace，并支持旧版本保留和快速回滚。

### 8.2 Agent Episode 与 Trace

一次用户任务对应一个 `AgentEpisode`。

建议记录：

```text
AgentEpisode
├── episodeId / conversationId / userId
├── agentVersion
├── originalGoal
├── assembledContextSummary
├── plan
├── modelCalls
├── policyDecisions
├── toolCalls
├── observations
├── evidence
├── verificationResults
├── recoveryActions
├── token / cost / elapsedTime
└── finalStatus / failureReason
```

Trace 记录必要的决策摘要和因果关系，不记录模型的完整私有推理过程。

### 8.3 Success Criteria

用户目标应被转换成受控、可验证的完成条件。

示例：

```yaml
goal: 查询 EC2025，如果已付款则发送回单

criteria:
  - payment_document_queried
  - payment_status_confirmed
  - receipt_sent_when_paid
  - receipt_not_sent_when_unpaid
  - final_answer_supported_by_evidence
```

`SuccessCriteria` 可以由 Planner 提议，但必须经过 Schema 和业务规则校验，不能完全依赖自由文本。

### 8.4 Evidence

Evidence 是 Verifier 作出判断的依据，应成为一等数据对象。

建议至少包含：

```text
Evidence
├── evidenceId
├── sourceType
├── sourceReference
├── claim
├── expectedValue
├── actualValue
├── collectedAt
└── reliabilityLevel
```

高风险动作应优先使用 read-after-write 反查，而不是只相信写操作的首次响应。

### 8.5 Verification

验证分为三个层次：

1. 协议验证：Schema、字段、类型和状态是否合法。
2. 节点验证：单个能力节点的业务输出是否满足预期。
3. 目标验证：所有完成条件是否满足，最终回答是否有证据支撑。

统一验证结果不应只有布尔值：

```text
VerificationResult
├── status: PASSED / FAILED / INCONCLUSIVE / WAITING_CONFIRMATION
├── reasonCode
├── message
├── evidence
├── retryable
└── recommendedAction
```

### 8.6 Execution Policy

Execution Policy 是模型行动和真实执行环境之间的强制控制层。

至少应支持：

```text
ExecutionBudget
├── maxSteps
├── maxModelCalls
├── maxToolCalls
├── maxTokens
├── maxCost
└── deadline

ToolPolicy
├── riskLevel
├── readOnly
├── idempotent
├── retryable
├── timeout
├── requiredPermissions
├── requiresConfirmation
└── dataClassification
```

### 8.7 Recovery

恢复决策应基于 Verification 和 Error Taxonomy：

```text
RecoveryAction
├── FINISH
├── RETRY_SAME_ACTION
├── RETRY_WITH_CHANGED_INPUT
├── REPLAN
├── ASK_USER
├── HUMAN_REVIEW
└── ABORT
```

每次恢复都必须消耗预算、记录原因，并受到最大次数限制。

### 8.8 Evaluation Harness

Evaluation Harness 用于批量执行固定业务场景并比较不同 Agent 版本。

评测必须同时检查：

- 最终业务状态。
- 工具调用和参数。
- 禁止发生的副作用。
- 最终回答与 Evidence 的一致性。
- 成本、Token、步骤数和延迟。

Grader 按可靠性组合：

```text
Deterministic Grader
+ Environment Grader
+ LLM Grader
+ Human Review
```

### 8.9 Release、灰度与回滚

建议发布流程：

```text
组件测试
  → Offline Eval
  → 历史失败回放
  → 安全评测
  → Shadow
  → 内部用户
  → 1% Canary
  → 5% / 20% / 50%
  → 全量
```

用户和会话应稳定分桶，避免同一会话在不同版本之间切换。

安全违规、重复副作用等指标属于硬门禁，不得通过综合评分抵消。

## 9. 评测指标

### 9.1 核心北极星指标

```text
Verified Task Success Rate
经过外部证据验证的任务成功率
```

### 9.2 质量指标

- Success Criteria 通过率。
- 最终回答与 Evidence 一致率。
- 用户重复提问或纠正率。
- 转人工率。
- 信息不足时正确追问率。

### 9.3 行为指标

- 工具选择正确率。
- 工具参数错误率。
- 无效工具调用率。
- 平均模型和工具调用次数。
- Replan 和 Retry 次数。
- 循环或预算耗尽率。

### 9.4 安全可靠性指标

- 越权工具调用率。
- 人工确认绕过率。
- 重复副作用率。
- 敏感信息泄露率。
- 不正确重试率。
- 可恢复失败的恢复成功率。

### 9.5 效率指标

- P50 / P95 任务延迟。
- 平均 Token。
- 平均单任务成本。
- 平均步骤数。
- 各模型和工具错误率。

## 10. 第一阶段基准业务场景

第一阶段围绕一个完整的小场景建设：

> 查询付款状态；如果已付款，则发送付款回单。

至少覆盖：

1. 付款单存在且已付款，成功发送一次回单。
2. 付款单存在但未付款，不发送回单。
3. 付款单不存在，返回明确结果。
4. 缺少付款单号，向用户追问。
5. 用户没有查询权限，安全终止。
6. 用户没有发送权限，不执行发送。
7. 查询工具超时，可安全重试。
8. 发送请求成功但响应丢失，通过反查避免重复发送。
9. 相同请求重复提交，不产生重复副作用。
10. 模型选择错误工具，被 Policy 或 Verifier 识别。
11. 最终回答与工具证据矛盾，被 Goal Verifier 拒绝。
12. 超过步骤、时间或调用预算，任务安全终止。

## 11. 实施路线

### 里程碑 1：看得见

建设：

- `AgentVersion`
- `AgentEpisode`
- `TraceEvent`
- 统一关联 ID
- 模型、工具、验证和恢复事件记录

完成标准：

- 任意一次 DAG 运行都能还原完整因果链。
- 能统计单任务步骤、耗时、模型调用和工具调用。
- 日志和 Trace 中不保存密钥及不必要的敏感数据。

### 里程碑 2：判得准

建设：

- `SuccessCriteria`
- `Evidence`
- `VerificationResult`
- `CapabilityVerifier`
- `GoalVerifier`

完成标准：

- 付款查询和回单发送均具有业务 Verifier。
- 最终成功必须有结构化 Evidence。
- 最终回答与 Evidence 矛盾时不能判定任务成功。

### 里程碑 3：控得住

建设：

- `ExecutionBudget`
- `ExecutionPolicy`
- 工具风险和幂等元数据
- 权限、确认、超时和副作用控制

完成标准：

- Runtime 能阻止越权和未确认的高风险行动。
- 每次运行都有明确的时间、步骤和工具调用上限。
- 非幂等动作不会被盲目自动重试。

### 里程碑 4：救得回来

建设：

- 统一错误分类。
- `RecoveryDecision`
- 安全重试。
- Replan、Ask User、Human Review 和 Abort。
- 断点和恢复状态。

完成标准：

- 查询超时能够安全重试。
- 参数错误不会原样重复调用。
- 发送结果不确定时先反查，不重复发送。
- 所有恢复动作有预算、有原因、有 Trace。

### 里程碑 5：测得出来

建设：

- `Scenario`
- `Dataset`
- `Grader`
- `EvaluationRun`
- V1/V2 对比报告

完成标准：

- 第一阶段至少有 20 个固定业务场景。
- 每个历史线上故障都能沉淀为回归用例。
- 可以输出成功率、安全性、成本和延迟对比。
- Eval 结果能够作为发布门禁。

### 里程碑 6：安全上线

建设：

- 稳定版本分桶。
- Shadow 工具或沙箱执行。
- Canary 配置。
- 指标告警。
- 自动熔断和快速回滚。

完成标准：

- 同一会话始终使用同一 Agent 版本。
- Shadow 版本不会产生真实副作用。
- 灰度期间可根据硬指标停止放量。
- 能回滚完整 AgentVersion，而非只回滚代码。

## 12. 第一阶段暂不建设

为避免范围失控，以下内容不是当前优先事项：

- 为了展示复杂度而增加更多子 Agent。
- 在没有 Eval 之前持续扩大 Planner 和 DAG 能力。
- 所有内部工具强制 MCP 化。
- 用 LLM Judge 替代可以确定性判断的业务规则。
- 一开始建设通用、跨行业的 Harness 平台。
- 一开始支持大规模并行 DAG 和分布式调度。
- 在没有权限、幂等和验证前开放高风险真实操作。

## 13. 项目完成愿景

当以下问题都能得到明确、可查询的答案时，项目可以认为具备了成熟 Harness 的基础：

```text
Agent 做了什么？
为什么这么做？
使用了哪个版本？
谁允许它这么做？
它看到了哪些必要信息？
调用了哪些工具？
产生了哪些真实副作用？
有什么证据证明任务完成？
失败属于什么类型？
为什么选择重试、重规划或终止？
新版本是否经过评测并优于旧版本？
出现风险时能否立即停止和回滚？
```

项目的最终目标不是保证模型永远不犯错，而是：

> 将模型的不确定性限制在可接受范围内，使任务执行有边界、结果有证据、失败可恢复、过程可审计、版本可评测、上线可回滚。

