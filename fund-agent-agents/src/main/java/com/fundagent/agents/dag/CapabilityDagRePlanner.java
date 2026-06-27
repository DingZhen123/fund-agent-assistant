package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.capability.CapabilityDefinition;
import com.fundagent.core.dag.DagEdge;
import com.fundagent.core.dag.NodeType;
import com.fundagent.core.dag.ReplanAction;
import com.fundagent.core.dag.ReplanContext;
import com.fundagent.core.dag.ReplanPatch;
import com.fundagent.core.llm.AgentLLMService;
import com.fundagent.core.llm.LLMCallerType;
import com.fundagent.core.llm.LLMRequest;
import com.fundagent.core.llm.LLMResponse;
import com.fundagent.core.llm.LLMResponseFormat;
import com.fundagent.core.llm.LLMService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class CapabilityDagRePlanner {
    private final LLMService llmService;
    private final AgentLLMService agentLLMService;
    private final CapabilityPlanningContextProvider planningContextProvider;

    public CapabilityDagRePlanner(LLMService llmService,
                                  CapabilityPlanningContextProvider planningContextProvider) {
        this.llmService = llmService;
        this.agentLLMService = null;
        this.planningContextProvider = planningContextProvider;
    }

    public CapabilityDagRePlanner(LLMService llmService,
                                  AgentLLMService agentLLMService,
                                  CapabilityPlanningContextProvider planningContextProvider) {
        this.llmService = llmService;
        this.agentLLMService = agentLLMService;
        this.planningContextProvider = planningContextProvider;
    }

    public ReplanPatch replan(ReplanContext context) {
        CapabilityPlanningContext planningContext = planningContextProvider.build(null, context.getUserMessage());
        String systemPrompt = buildSystemPrompt(planningContext);
        String userMessage = buildUserMessage(context);
        String schema = buildReplanPatchSchema(planningContext.getCapabilities());
        String raw;
        if (agentLLMService != null && context.getTraceContext() != null) {
            LLMResponse response = agentLLMService.call(LLMRequest.builder()
                    .traceContext(context.getTraceContext())
                    .callerType(LLMCallerType.REPLANNER)
                    .callerName("CapabilityDagRePlanner")
                    .capability("dag.replanning")
                    .systemPrompt(systemPrompt)
                    .history(List.of())
                    .currentMessage(userMessage)
                    .responseFormat(LLMResponseFormat.jsonSchema("replan_patch", schema))
                    .metadata(Map.of("failedNodeId",
                            context.getFailedNode() != null ? context.getFailedNode().getNodeId() : ""))
                    .build());
            context.setTraceContext(response.getTraceContext());
            raw = response.getContent();
        } else {
            raw = llmService.chatStructured(
                    systemPrompt,
                    List.of(),
                    userMessage,
                    "replan_patch",
                    schema);
        }
        log.info("CapabilityDagRePlanner raw: {}", raw);
        return JSON.parseObject(raw, ReplanPatch.class);
    }

    private String buildSystemPrompt(CapabilityPlanningContext planningContext) {
        return """
                你是企业级DAG Agent Runtime中的CapabilityDagRePlanner。
                你的职责是在DAG执行失败后，基于原始目标、原图、已执行Observation和失败节点信息，生成恢复补丁ReplanPatch。

                可用能力:
                %s

                规则:
                1. 只输出符合JSON Schema的ReplanPatch。
                2. 第一版不能修改、删除或重写历史节点，只允许追加恢复节点。
                3. 如果DAG没有失败，action必须为NOOP，append_nodes为空。
                4. 如果无法恢复，action必须为FAIL，并给出stop_message。
                5. 如果可以恢复，action必须为APPEND_NODES。
                6. append_nodes只能依赖已成功或已跳过的节点，不能依赖FAILED节点。
                7. append_nodes必须至少包含一个FINAL_ANSWER节点，用于向用户说明恢复后的结果或失败原因。
                8. capability必须逐字使用可用能力中的名称，不要编造能力。
                9. node_type必须与所选capability的nodeType一致。
                10. condition只作为文本保存，不要依赖condition表达式执行。
                """.formatted(planningContext.getCapabilitiesDescription());
    }

    private String buildUserMessage(ReplanContext context) {
        JSONObject user = new JSONObject();
        user.put("user_message", context.getUserMessage());
        user.put("bound_dag_plan", JSON.toJSON(context.getBoundDagPlan()));
        user.put("run_result", JSON.toJSON(context.getRunResult()));
        user.put("failed_node", JSON.toJSON(context.getFailedNode()));
        user.put("failed_result", JSON.toJSON(context.getFailedResult()));
        user.put("failed_completion", JSON.toJSON(context.getFailedCompletion()));
        return user.toJSONString();
    }

    private String buildReplanPatchSchema(List<CapabilityDefinition> capabilities) {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("action", enumStringSchema(ReplanAction.APPEND_NODES.name(),
                ReplanAction.FAIL.name(), ReplanAction.NOOP.name()));
        properties.put("reason", stringSchema());

        JSONObject appendNodes = new JSONObject();
        appendNodes.put("type", "array");
        appendNodes.put("items", buildNodeSchema(capabilities));
        properties.put("append_nodes", appendNodes);

        JSONObject appendEdges = new JSONObject();
        appendEdges.put("type", "array");
        appendEdges.put("items", buildEdgeSchema());
        properties.put("append_edges", appendEdges);

        properties.put("stop_message", stringSchema());

        JSONObject metadata = new JSONObject();
        metadata.put("type", "object");
        metadata.put("additionalProperties", false);
        metadata.put("properties", new JSONObject());
        metadata.put("required", List.of());
        properties.put("metadata", metadata);

        root.put("properties", properties);
        root.put("required", List.of("action", "reason", "append_nodes", "append_edges", "stop_message", "metadata"));
        return root.toJSONString();
    }

    private JSONObject buildNodeSchema(List<CapabilityDefinition> capabilities) {
        JSONObject schema = new JSONObject();
        JSONArray branches = new JSONArray();
        for (CapabilityDefinition capability : capabilities) {
            branches.add(buildCapabilityNodeSchema(capability));
        }
        schema.put("anyOf", branches);
        return schema;
    }

    private JSONObject buildCapabilityNodeSchema(CapabilityDefinition capability) {
        JSONObject node = new JSONObject();
        node.put("type", "object");
        node.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("node_id", stringSchema());
        properties.put("name", stringSchema());
        properties.put("node_type", enumStringSchema(capability.getNodeType()));
        properties.put("capability", enumStringSchema(capability.getName()));
        properties.put("instruction", stringSchema());

        JSONObject dependsOn = new JSONObject();
        dependsOn.put("type", "array");
        dependsOn.put("items", stringSchema());
        properties.put("depends_on", dependsOn);

        JSONObject expectedOutputs = new JSONObject();
        expectedOutputs.put("type", "array");
        expectedOutputs.put("items", stringSchema());
        properties.put("expected_outputs", expectedOutputs);

        properties.put("agent", enumStringSchema(resolveAgent(capability.getNodeType())));

        JSONObject metadata = new JSONObject();
        metadata.put("type", "object");
        metadata.put("additionalProperties", false);
        metadata.put("properties", new JSONObject());
        metadata.put("required", List.of());
        properties.put("metadata", metadata);

        node.put("properties", properties);
        node.put("required", List.of("node_id", "name", "node_type", "capability",
                "instruction", "depends_on", "expected_outputs", "agent", "metadata"));
        return node;
    }

    private JSONObject buildEdgeSchema() {
        JSONObject edge = new JSONObject();
        edge.put("type", "object");
        edge.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("from", stringSchema());
        properties.put("to", stringSchema());
        properties.put("condition", stringSchema());
        edge.put("properties", properties);
        edge.put("required", List.of("from", "to", "condition"));
        return edge;
    }

    private String resolveAgent(String nodeType) {
        if (nodeType == null) {
            return "ReasonAgent";
        }
        return switch (NodeType.valueOf(nodeType)) {
            case QUERY -> "QueryAgent";
            case ACTION -> "ActionAgent";
            case KNOWLEDGE_SEARCH -> "KnowledgeAgent";
            case LLM_REASON -> "ReasonAgent";
            case ASK_USER -> "AskUserAgent";
            case FINAL_ANSWER -> "AnswerAgent";
            case VERIFY -> "VerifierAgent";
        };
    }

    private JSONObject enumStringSchema(String... values) {
        JSONObject schema = stringSchema();
        schema.put("enum", List.of(values));
        return schema;
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }
}
