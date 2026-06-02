# DAG ReAct Agent 后续实施计划

## 1. 当前状态

### 1.1 已提交能力

最近一次已提交：

```text
f42a140 Refactor tool governance pipeline
```

已完成的工具治理第一版：

- `ToolDefinition` 扩展工具元数据。
- `tools.yaml` 增加 `intents / providerType / requiresAuth / requiresConfirmation`。
- `ToolCatalogProvider / ToolCatalog / ToolMetadata` 建立轻量工具目录层。
- `LocalToolCatalogProvider` 提供本地工具目录元数据。
- `ToolSelector / ToolSelectionStage / DefaultToolSelector` 建立候选工具选择 Pipeline。
- `RuleBasedDomainStage / RuleBasedIntentStage / CatalogFilterStage / ToolRankingStage / TopKStage` 建立第一版规则选择器。
- `ToolProvider / LocalToolProvider` 建立工具执行 Provider 插槽。
- `ToolRegistry` 从本地工具仓库演进为工具执行分发器。
- `ChatService` 已接入 `ToolSelector`，可以在复杂任务链路中观测候选工具选择结果。

### 1.2 当前未提交改动

当前工作区还有一批未提交改动：

- `GraphTaskPlanner` 已改为支持候选工具输入。
- 新增 `ToolSchemaResolver`，用于根据候选工具名按需解析完整 `ToolDefinition`。
- `ChatService` 已将链路改为：

```text
ToolSelector
  -> ToolSchemaResolver
  -> GraphTaskPlanner(candidateTools)
```

- `GraphTaskPlanner` 已开始按候选工具生成 JSON Schema，并使用 `anyOf` 将 `tool_name` 与 `tool_params` 绑定。
- 新增 `docs/dag-agent-design.md`，记录 DAG Agent 架构共识。

这批改动已通过编译：

```text
mvn -q -pl fund-agent-server -am compile
```

但还没有提交。

### 1.3 当前设计共识

已在 `docs/dag-agent-design.md` 记录：

- DAG Agent 应按能力节点构造，而不是按工具调用构造。
- 工具是 DAG 节点能力之一，不是 DAG 默认入口。
- Planner 先输出抽象 capability DAG，再进入工具绑定。
- `capability` 来自受控 `CapabilityCatalog`，不是 LLM 自由生成文本。
- Agent 默认按能力划分，而不是按业务领域划分。
- 第一版 DAG 执行按拓扑串行，不做并发。
- `LLM_REASON` 是独立节点，由独立能力 Agent 执行。
- `ToolSelector` 放在 `ToolBinder` 阶段，而不是 Planner 前。
- 节点后做轻量校验，整图后做 Final Verifier。
- RePlanner 第一版只追加节点，不修改已有节点。
- 第一版暂不处理高风险动作确认。
- 新 DAG 第一版完整跑通后，下线旧 simple chain。

## 2. 总体目标

目标不是继续堆叠工具规则，而是把当前项目演进为：

```text
Capability Planner
  -> ToolBinder
  -> DAG Scheduler
  -> Node Agent Executor
  -> Node Completion Check
  -> Final Verifier
  -> RePlanner
  -> AnswerAgent
```

第一版目标：

```text
协议上是 DAG
执行上按拓扑串行
节点按能力划分
工具按 capability node 绑定
子 Agent 可以执行节点
Observation 可追踪
最终能替换旧 simple chain
```

## 3. 阶段计划

## 阶段 0：收尾当前未提交改动

目标：

```text
确认候选工具动态 schema 改动是否保留。
如果保留，提交为独立 commit。
```

需要做：

- 检查 `GraphTaskPlanner` 当前 `anyOf` schema 是否符合 OpenAI Structured Outputs 支持范围。
- 检查候选工具为空时，Planner 是否能生成合法 `ASK_USER -> FINAL_ANSWER` 计划。
- 检查 `TaskPlanValidator` 对 `anyOf` 计划输出是否仍能正确校验。
- 补充至少一组手工验证日志：

```text
查一下 EC2025，如果付款了把回单发我
  -> ToolSelector: queryPaymentDocuments, sendReceiptFiles
  -> GraphTaskPlanner: 只看到这两个工具 schema
```

产出：

```text
commit: Wire candidate tool schemas into graph planner
```

## 阶段 1：CapabilityCatalog

目标：

```text
建立受控 capability 索引。
让 Planner 输出 capability，而不是自由生成工具名。
```

新增模块建议：

```text
com.fundagent.core.capability
  CapabilityDefinition
  CapabilityCatalog
  CapabilityCatalogProvider
  CapabilityValidator
```

配置文件建议：

```text
capabilities.yaml
```

示例：

```yaml
capabilities:
  finance.payment.query:
    nodeType: QUERY
    domain: finance
    intents: [query]
    description: 查询付款状态、付款金额、收付款方、付款时间
    bindableTools:
      - queryPaymentDocuments

  finance.receipt.send:
    nodeType: ACTION
    domain: finance
    intents: [send, download]
    description: 发送或下载付款回单文件
    bindableTools:
      - sendReceiptFiles

  conversation.answer:
    nodeType: FINAL_ANSWER
    domain: general
    intents: [answer]
    description: 根据已有上下文和节点结果回复用户
```

产出：

- `CapabilityCatalog` 可以加载能力目录。
- `CapabilityValidator` 可以校验 Planner 输出是否只使用受控 capability。
- 文档明确 capability 与 tool 的关系：

```text
Capability = 业务能力协议
Tool = 具体执行方式
```

## 阶段 2：能力节点 DAG 协议

目标：

```text
从 TaskStep 工具图升级为能力节点图。
```

新增或演进模型：

```text
DagPlan
DagNode
DagEdge
NodeType
NodeStatus
NodeExecutionResult
```

第一版 `NodeType`：

```text
QUERY
ACTION
LLM_REASON
ASK_USER
FINAL_ANSWER
VERIFY
```

第一版可以暂不支持：

```text
并行
高风险确认
复杂条件表达式
修改已有节点
```

节点协议需要包含：

```text
node_id
name
node_type
capability
instruction
depends_on
expected_outputs
agent
metadata
```

产出：

- Planner 输出受 JSON Schema 约束的能力 DAG。
- `capability` 字段使用 enum。
- 服务端校验 DAG 结构和 capability 合法性。

## 阶段 3：Capability Planner

目标：

```text
替换当前 GraphTaskPlanner 的“具体工具调用图”能力。
让 Planner 先输出 capability DAG。
```

输入：

```text
用户问题
MemoryContext
CapabilityCatalog 摘要
受控 JSON Schema
```

输出：

```text
DagPlan(capability nodes)
```

注意：

- Planner 不直接输出 `tool_name`。
- Planner 只输出 `capability`。
- `capability` 必须来自 enum。
- 如果不需要工具，也可以输出 `LLM_REASON / FINAL_ANSWER` 节点。

产出：

```text
用户“你好” -> FINAL_ANSWER
用户“解释 DAG ReAct” -> LLM_REASON -> FINAL_ANSWER
用户“查 EC2025” -> QUERY(finance.payment.query) -> FINAL_ANSWER
```

## 阶段 4：ToolBinder

目标：

```text
把 capability node 绑定为具体工具调用。
```

新增模块建议：

```text
ToolBinder
ToolBindingRequest
ToolBindingResult
```

链路：

```text
DagNode(capability=finance.payment.query)
  -> ToolSelector
  -> ToolSchemaResolver
  -> ToolBindingPlanner
  -> TOOL_CALL executable node
```

第一版可简化：

```text
CapabilityDefinition.bindableTools 只有一个工具时，直接绑定。
多个工具时，用 ToolSelector / LLM rerank。
```

产出：

- `finance.payment.query -> queryPaymentDocuments`
- `finance.receipt.send -> sendReceiptFiles`
- 只对绑定候选工具加载完整 schema。

## 阶段 5：Node Agent Executor

目标：

```text
节点由能力型 Agent 执行，而不是 Orchestrator 内部 switch 写死所有逻辑。
```

第一版 Agent：

```text
QueryAgent
ActionAgent
ReasonAgent
VerifierAgent
AnswerAgent
```

职责：

```text
QueryAgent: 执行 QUERY capability，通常绑定工具。
ActionAgent: 执行 ACTION capability，通常绑定工具。
ReasonAgent: 执行 LLM_REASON。
VerifierAgent: 执行 VERIFY / Final Verifier。
AnswerAgent: 执行 FINAL_ANSWER。
```

子 Agent 输入：

```text
DagNode
GraphState
MemoryContext
CapabilityDefinition
BoundToolDefinition
```

子 Agent 输出：

```text
NodeExecutionResult
Observation
status
next_action
```

## 阶段 6：串行 DAG Scheduler

目标：

```text
按拓扑顺序串行执行 DAG。
```

第一版 Scheduler：

```text
find ready nodes
按顺序执行第一个或逐个执行 ready nodes
写入 Observation
更新 NodeStatus
执行 Node Completion Check
继续下一轮
```

暂不支持：

```text
并行
资源锁
复杂条件边
取消传播
```

但协议保留：

```text
depends_on
condition
```

## 阶段 7：Node Completion Check + Final Verifier

目标：

```text
节点后做轻量校验。
整图后做全局校验。
```

节点后校验：

```text
节点是否成功
required outputs 是否齐全
是否需要 retry / ask user / replan / fail
```

整图后校验：

```text
用户目标是否完成
Observation 是否冲突
是否需要补节点
最终回答是否可以生成
```

第一版：

```text
Node Completion Check: 规则
Final Verifier: 可以用 LLM structured output
```

## 阶段 8：RePlanner

目标：

```text
当节点或整图验证不通过时，可以追加节点。
```

第一版只允许：

```text
append node
ask user
fail task
```

不允许：

```text
修改已执行节点
删除已执行节点
隐式改写 Observation
```

后续扩展：

```text
replace pending node
skip pending node
retry failed node
graph versioning
```

## 阶段 9：旧 simple chain 下线

触发条件：

```text
新 DAG 第一版可以覆盖：
  普通问答
  简单工具查询
  两步工具任务
  缺参追问
  工具失败
  最终答案生成
```

下线方式：

```text
保留旧链路 feature flag
灰度切换到新 DAG Runtime
日志观察
确认稳定后移除旧 PlannerAgent -> ExecutorAgent 简单链路
```

## 4. 优先级建议

当前最推荐顺序：

```text
P0: 收尾并提交当前候选工具 schema 改动
P1: CapabilityCatalog
P2: 能力节点 DAG 协议
P3: Capability Planner
P4: ToolBinder
P5: Node Agent Executor
P6: 串行 DAG Scheduler
P7: Node Completion Check + Final Verifier
P8: RePlanner append-only
P9: 下线旧 simple chain
```

## 5. 当前不做的事情

第一版明确不做：

```text
并行 DAG
高风险动作确认
复杂条件表达式引擎
完整权限系统
MCP / RPA Provider 真实接入
修改已有节点的 RePlan
长期记忆 mem0
RAG 知识库
```

这些能力后续再作为二期或三期扩展。

## 6. 下一步建议

下一步建议先处理当前未提交改动：

```text
确认是否保留候选工具动态 schema
若保留，提交为独立 commit
```

然后进入：

```text
CapabilityCatalog 设计与实现
```

这是后续 DAG Agent 的关键地基。没有受控 capability，Planner 输出抽象能力图就不稳定。
