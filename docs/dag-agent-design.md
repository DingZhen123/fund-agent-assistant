# 企业级 DAG ReAct Agent 设计说明书（草案）

## 1. 背景

当前项目正在从“资金系统智能助手”升级为“企业级 DAG ReAct Agent Runtime”。现有系统已经具备以下基础能力：

- `PlannerAgent -> ExecutorAgent` 的旧版简单链路。
- `GraphTaskPlanner -> GraphOrchestrator -> GraphAnswerGenerator` 的顺序图执行雏形。
- `TaskPlan / TaskStep / Observation / GraphState` 等图运行时数据结构。
- `ToolCatalog / ToolSelector / ToolSchemaResolver / ToolProvider` 的第一版工具治理边界。
- `ToolRegistry` 已从本地工具仓库逐步演进为工具执行分发器。

但当前图执行仍然更接近“顺序工具调用计划”，还不是完整的 DAG ReAct Agent。

目标是设计一个可扩展、可治理、可审计的企业级 Agent Runtime：

```text
Planner 规划任务图
  -> 子 Agent 执行节点
  -> 子 Agent 内部 ReAct
  -> Observation 回写图状态
  -> Verifier 判断是否完成
  -> RePlanner 决定继续、重试、补图、追问或结束
```

## 2. 目标形态

理想的 DAG Agent 不是“一个 Planner 直接调用工具”，而是一个多层运行时：

```text
User Request
  -> Request / Capability Router
  -> DAG Planner
  -> DAG Scheduler
  -> Node Agent Executor
  -> Observation Store
  -> Verifier
  -> RePlanner
  -> Final Answer
```

其中：

- `DAG Planner` 负责把用户目标拆成子任务图。
- `DAG Scheduler` 负责判断节点依赖、串行/并行、跳过、重试。
- `Node Agent Executor` 负责执行单个节点。
- 每个子 Agent 内部可以是一个小型 ReAct 循环。
- `Verifier` 判断用户目标是否完成。
- `RePlanner` 根据执行结果补充或修改后续任务。

## 3. 核心原则

### 3.1 DAG Runtime 不等于 Tool Runtime

工具调用只是 DAG 节点的一种能力，不应该成为所有图任务的默认前置步骤。

节点能力应该包括：

```text
LLM_REASON
TOOL_CALL
KNOWLEDGE_SEARCH
ASK_USER
CONFIRM_USER
VERIFY
FINAL_ANSWER
```

后续可以扩展：

```text
SUB_AGENT
HUMAN_REVIEW
WAIT_EVENT
REPLAN
```

### 3.2 Planner 规划子任务，不直接执行细节

Planner 的职责是输出任务图，而不是亲自完成所有工具参数推理和执行。

Planner 应该输出：

```text
目标
节点列表
节点类型
依赖关系
输入来源
预期输出
风险等级
执行 Agent
成功/失败后续策略
```

### 3.3 子 Agent 内部 ReAct

一个 DAG 节点可以由一个子 Agent 执行。子 Agent 内部可以做：

```text
Reason Summary
  -> Act
  -> Observe
  -> Decide continue / stop / ask / fail
```

注意：系统只记录 reasoning summary，不记录完整 chain-of-thought。

### 3.4 先抽象能力，再绑定工具

Planner 不应该一开始面对所有具体工具。更推荐两阶段：

```text
抽象任务图：
  NEED_FINANCE_QUERY
  NEED_RECEIPT_SEND
  FINAL_ANSWER

工具绑定：
  NEED_FINANCE_QUERY -> queryPaymentDocuments
  NEED_RECEIPT_SEND -> sendReceiptFiles
```

这样可以避免工具空间过大，也方便 MCP/RPA/本地工具统一治理。

### 3.5 Capability 是受控索引，不是自由文本

抽象能力图中的 `capability` 不能由 LLM 自由生成。系统需要维护一个受控能力目录：

```text
CapabilityCatalog
```

示例：

```yaml
capabilities:
  finance.payment.query:
    domain: finance
    intents: [query]
    description: 查询付款状态、付款金额、收付款方、付款时间
    bindableTools:
      - queryPaymentDocuments

  finance.receipt.send:
    domain: finance
    intents: [send, download]
    description: 发送或下载付款回单文件
    bindableTools:
      - sendReceiptFiles

  conversation.answer:
    domain: general
    intents: [answer]
    description: 不调用外部工具，直接根据已有上下文回答用户
```

Planner 生成抽象能力图时，`capability` 字段必须通过以下三层约束：

```text
Prompt 注入 CapabilityCatalog 摘要
Structured Output JSON Schema 使用 enum 限制 capability
服务端 CapabilityValidator 校验 capability 是否存在
```

也就是说：

```text
Capability 是系统定义的稳定协议，不是 LLM 自由生成的文本。
```

如果能力目录过大，可以先让 LLM 选择能力领域或能力类别，再展开该类别下的 capability 列表，最终仍然通过 JSON Schema enum 约束输出。

## 4. 当前架构与目标架构差距

### 4.1 当前架构

当前复杂链路：

```text
ChatService
  -> TaskRouter
  -> ToolSelector
  -> ToolSchemaResolver
  -> GraphTaskPlanner
  -> GraphOrchestrator
  -> ToolRegistry / ToolProvider
  -> GraphAnswerGenerator
```

当前问题：

- ToolSelector 被放在 GraphTaskPlanner 前面，导致图运行时天然偏工具任务。
- StepType 只有 `TOOL_CALL / ASK_USER / FINAL_ANSWER`，节点能力过窄。
- GraphOrchestrator 仍然是顺序执行，不是真正 DAG Scheduler。
- Planner 生成的是具体工具调用图，而不是抽象能力图。
- 缺少子 Agent 运行模型。
- 缺少 Verifier / RePlanner。
- 缺少条件边、并行节点、失败分支。

### 4.2 目标架构

建议目标链路：

```text
ChatService
  -> RequestRouter
  -> CapabilityRouter
  -> DagPlanner
  -> ToolBinder / AgentBinder
  -> DagScheduler
  -> NodeAgentExecutor
  -> Verifier
  -> RePlanner
  -> FinalAnswerGenerator
```

## 5. DAG 数据模型草案

### 5.1 DagPlan

```json
{
  "dag_id": "",
  "goal": "",
  "nodes": [],
  "edges": [],
  "metadata": {}
}
```

### 5.2 DagNode

```json
{
  "node_id": "n1",
  "name": "查询付款状态",
  "type": "TOOL_CALL",
  "agent": "FinanceAgent",
  "instruction": "查询 EC2025 的付款状态",
  "inputs": {},
  "expected_outputs": ["paymentStatus", "receiptAvailable"],
  "risk_level": "low",
  "retry_policy": {},
  "requires_confirmation": false
}
```

### 5.3 DagEdge

```json
{
  "from": "n1",
  "to": "n2",
  "condition": "$n1.paymentStatus == '已付款'"
}
```

第一版可以先只支持：

```text
depends_on
```

后续再支持：

```text
condition
on_success
on_failure
```

## 6. 节点类型设计

### 6.1 LLM_REASON

用于纯推理、解释、摘要、判断，不调用外部工具。

适用：

- 解释概念。
- 总结上下文。
- 基于已有 Observation 做分析。

当前共识：

```text
LLM_REASON 作为独立节点存在。
LLM_REASON 由独立能力 Agent 执行，例如 ReasonAgent / AnalysisAgent。
它输出结构化中间结论，供后续节点、Verifier 或 RePlanner 使用。
```

它和 `FINAL_ANSWER` 的区别：

```text
LLM_REASON 面向系统，是中间推理/分析结果。
FINAL_ANSWER 面向用户，是最终自然语言回复。
```

### 6.2 TOOL_CALL

调用本地 / MCP / RPA 工具。

执行前必须经过：

```text
ToolSchemaValidator
ToolPolicyValidator
PermissionValidator
RiskControl
```

### 6.3 KNOWLEDGE_SEARCH

用于企业知识库检索。

适用：

- 制度文档。
- 操作手册。
- FAQ。
- 历史案例。

不适用：

- 实时业务单据状态。
- 精确业务数据查询。

### 6.4 ASK_USER

用于缺少必要信息时追问用户。

### 6.5 CONFIRM_USER

用于高风险动作执行前确认。

适用：

- 发送文件。
- 提交审批。
- 修改数据。
- 发起付款。

### 6.6 VERIFY

用于判断任务目标是否完成。

### 6.7 FINAL_ANSWER

生成最终用户可读回复。

## 7. 子 Agent 设计

### 7.1 子 Agent 的角色

子 Agent 负责执行一个或一类节点。当前初步共识是：

```text
Agent 默认按能力划分，而不是按业务领域划分。
业务领域通过 capability / domain / tool scope 体现。
领域专用 Agent 作为可选扩展，不作为默认模型。
```

原因：

- 如果按业务领域划分，例如 `FinanceAgent / HRAgent / ContractAgent`，通用 Runtime 容易被业务领域绑定。
- 每接一个新领域都新增 Agent，会导致扩展成本升高。
- 查询、动作、知识检索、分析、校验、回答这些执行模式在不同业务领域中高度复用。

更推荐的分层是：

```text
Agent = 怎么做
Capability = 要做什么业务能力
Tool = 具体调用什么系统
```

示例：

```text
QueryAgent
ActionAgent
KnowledgeAgent
AnalysisAgent
VerifierAgent
AnswerAgent
```

其中：

```text
QueryAgent + capability=finance.payment.query
QueryAgent + capability=hr.employee.query
ActionAgent + capability=finance.receipt.send
KnowledgeAgent + capability=contract.policy.search
```

这样新接入业务领域时，优先新增 capability 和 tool，而不是新增领域 Agent。

### 7.2 子 Agent 输入

```json
{
  "node": {},
  "graph_state": {},
  "memory_context": {},
  "available_tools": [],
  "policy_context": {}
}
```

### 7.3 子 Agent 输出

```json
{
  "status": "SUCCESS | FAILED | NEED_USER | NEED_REPLAN",
  "observation": {},
  "suggested_next_action": "CONTINUE | ASK_USER | REPLAN | FAIL"
}
```

## 8. 子 Agent 内部 ReAct

每个子 Agent 内部可以有一个有限循环：

```text
max_internal_steps = 3

Reason Summary
  -> Action
  -> Observation
  -> Stop / Continue
```

必须限制：

- 最大步数。
- 最大工具调用次数。
- 最大 token。
- 是否允许高风险工具。
- 是否允许访问敏感数据。

## 9. DAG 调度问题

### 9.1 串行还是并行

当前初步共识是：

```text
串行或并行不应该由系统预先固定，而应该由用户问题、任务依赖和节点输入输出关系决定。
```

也就是说：

```text
用户任务天然有依赖 -> 串行
用户任务包含多个互不依赖子任务 -> 可以并行
节点结果会影响后续判断 -> 串行或条件边
```

实现层面达成进一步共识：

```text
第一版只支持串行执行。
协议保留 DAG 的节点依赖表达，但 Scheduler 按拓扑顺序串行执行 ready nodes。
并发执行后续再扩展。
```

原因：

- 并发会引入 GraphState 并发写入问题。
- Observation 合并和冲突处理会更复杂。
- 工具调用需要处理并发限流、超时和取消。
- 节点失败传播和资源锁会增加第一版复杂度。
- 当前更重要的是先跑通节点模型、子 Agent 执行、Observation、Verifier / RePlanner。

DAG Scheduler 的职责不是固定“串行执行”，而是：

```text
根据节点依赖找出当前可运行节点
第一版逐个串行执行 ready nodes
后续版本再支持多个 ready nodes 并行执行
```

并行执行后续需要解决：

- 共享 GraphState 的写入冲突。
- Observation 合并。
- 节点超时。
- 并行工具的速率限制。
- 并行失败后的降级策略。

### 9.2 节点执行状态

```text
PENDING
RUNNING
SUCCESS
FAILED
SKIPPED
WAITING_USER
NEED_REPLAN
```

### 9.3 调度器职责

```text
找出可执行节点
判断依赖是否完成
执行节点
记录状态
处理失败和重试
触发 verifier / replanner
```

## 10. Verifier 与 RePlanner

### 10.1 Verifier

Verifier 不只在整图结束后执行。当前共识是：

```text
每个节点执行后都应该有 verification gate。
节点后 verification gate 默认先做轻量规则校验。
必要时再调用 VerifierAgent 做 LLM 语义校验。
整图完成后还需要 Final Verifier 做全局目标校验。
```

节点后校验关注：

```text
这个节点是否完成？
节点 required outputs 是否齐全？
节点输出是否足够供后续节点使用？
是否需要 retry / ask user / replan / fail？
```

整图后校验关注：

```text
用户整体目标是否完成？
关键需求是否遗漏？
多个 Observation 是否冲突？
最终回复是否可信？
```

Verifier 判断：

- 用户目标是否完成。
- Observation 是否足够。
- 是否存在冲突。
- 是否需要补充查询。
- 是否需要问用户。

输出：

```json
{
  "status": "PASS | NEED_MORE_INFO | NEED_REPLAN | FAILED",
  "reason": "",
  "missing_items": [],
  "next_action": "FINAL_ANSWER | ASK_USER | REPLAN | FAIL"
}
```

第一版建议：

```text
节点后实现规则型 Node Completion Check。
整图后实现 Final Verifier。
节点后 LLM Verifier 作为可选扩展，后续在复杂分析、高风险动作、规则不确定时启用。
```

执行链路：

```text
Node Execute
  -> Node Completion Check
  -> optional VerifierAgent
  -> Scheduler 决定继续 / 重试 / 追问 / RePlan / 失败

Graph Completed
  -> Final Verifier
  -> AnswerAgent
```

### 10.2 RePlanner

RePlanner 基于当前 GraphState 修改任务图：

```text
append node
skip node
retry node
replace node
ask user
fail task
```

第一版建议只支持：

```text
append node
ask user
fail task
```

当前进一步收敛：

```text
RePlanner 第一版只允许追加节点，不允许修改已有节点。
```

原因：

- 修改已有节点会带来执行历史和审计一致性问题。
- 已执行节点的 Observation 不能被隐式改写。
- 追加节点更容易维护 Trace，也更容易解释“系统为什么继续做了什么”。
- 第一版应优先跑通 RePlan 闭环，而不是支持复杂图编辑。

后续版本可以扩展：

```text
skip pending node
replace pending node
retry failed node
mark node obsolete
```

但即使后续允许修改，也应该只允许修改未执行节点，并保留图版本历史。

## 11. 工具治理在 DAG 中的位置

工具治理不应该作为 DAG 入口处的默认前置步骤。当前共识是：

```text
Planner 前只放 CapabilityCatalog。
Planner 输出抽象 capability DAG。
ToolSelector 放在 ToolBinder 阶段。
ToolBinder 对每个需要工具的 capability node 选择并绑定具体工具。
```

也就是说：

```text
Planner 负责决定“需要什么能力”。
ToolSelector 负责决定“这个能力节点可以用哪些工具”。
ToolBinder 负责决定“这个能力节点最终绑定哪个具体工具和参数”。
```

推荐链路：

```text
User Request
  -> Capability Planner
       输入：CapabilityCatalog
       输出：Capability DAG

  -> ToolBinder
       遍历 DAG 中需要工具的 capability node
       对每个 node 调用 ToolSelector
       ToolSchemaResolver 加载候选工具完整 schema
       绑定成 TOOL_CALL node 或工具子图

  -> DAG Scheduler
  -> NodeAgentExecutor
```

不推荐的默认链路：

```text
User Request
  -> ToolSelector
  -> Planner
```

原因：

- Planner 之前还不知道任务会拆成哪些 capability node。
- 过早选择工具会让工具选择器承担任务规划职责。
- 非工具节点，例如 `LLM_REASON / ASK_USER / FINAL_ANSWER`，不应该触发工具选择。
- 工具选择应围绕具体 capability node 进行，而不是围绕整条用户消息粗暴选择。

例外：

```text
对于特别明确的 simple tool task，可以保留快捷路径。
例如“查一下 EC2025 的付款状态”可以通过 CapabilityRouter 直接进入工具绑定。
但这属于优化路径，不是主 DAG 架构。
```

## 12. 建议实施路线

### 阶段一：节点能力模型

目标：

```text
扩展 StepType / NodeType
引入 LLM_REASON
让 GraphTaskPlanner 可以生成非工具节点
```

### 阶段二：CapabilityRouter

目标：

```text
判断 needsTool / needsKnowledge / needsUserInput / directAnswer
只有 needsTool=true 才调用 ToolSelector
```

### 阶段三：抽象能力图

目标：

```text
Planner 先输出抽象能力节点
再对 NEED_TOOL 节点做工具绑定
```

### 阶段四：子 Agent 执行器

目标：

```text
NodeAgentExecutor
FinanceAgent / KnowledgeAgent / VerifierAgent
子 Agent 内部 ReAct
```

### 阶段五：Verifier / RePlanner

目标：

```text
每轮图执行后验证目标完成度
支持补节点和追问用户
```

### 阶段六：并行 DAG

目标：

```text
支持无依赖节点并行执行
支持条件边
支持失败分支
```

## 13. 当前待讨论问题

1. Planner 是否先输出抽象能力图，再绑定具体工具？（已达成初步共识：先输出受控 capability 图，再绑定工具）
2. 子 Agent 是按领域划分，还是按节点能力划分？
3. 第一版是否只支持串行 DAG？
4. LLM_REASON 节点是否需要独立 Agent 执行？
5. ToolSelector 应该在 Planner 前，还是 ToolBinder 阶段？（已达成初步共识：ToolSelector 放在 ToolBinder 阶段）
6. Verifier 是每个节点后执行，还是整图后执行？（已达成初步共识：节点后轻量校验 + 可选 LLM Verifier，整图后 Final Verifier）
7. RePlanner 第一版允许修改已有节点，还是只能追加节点？（已达成初步共识：第一版只追加节点，不修改已有节点）
8. GraphState 的 Observation 如何设计才能支持并行？（第一版暂不考虑并行，后续扩展时再设计并发写入和 Observation 合并）
9. 高风险动作确认是独立节点，还是 ToolPolicy 拦截后动态插入节点？（第一版暂不考虑高风险确认，避免复杂度过高）
10. 旧 simple chain 何时完全下线？（新 DAG 第一版完整跑通后下线旧 simple chain）

## 14. 当前共识草案

当前建议先达成以下共识：

```text
DAG Agent 应按能力节点构造，而不是按工具调用构造。
工具是 DAG 节点能力之一，不是 DAG 的默认入口。
Planner 负责规划子任务图。
子 Agent 默认按能力划分，业务领域由 capability / domain / tool scope 体现。
子 Agent 负责执行节点，内部可以 ReAct。
Scheduler 负责节点依赖和执行顺序；第一版按拓扑串行执行，后续再扩展并行。
Verifier / RePlanner 负责闭环。
RePlanner 第一版只追加节点，不修改已有节点。
第一版暂不处理高风险动作确认。
新 DAG 第一版完整跑通后，再下线旧 simple chain。
```

后续讨论应先围绕“节点能力模型”和“Planner 输出协议”展开。
