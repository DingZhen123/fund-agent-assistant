package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityDefinition;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.NodeType;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.MemoryAssembler;
import com.fundagent.core.memory.MemoryContext;
import com.fundagent.core.memory.MemoryUseCase;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CapabilityDagPlanner {
    private final LLMService llmService;
    private final CapabilityCatalog capabilityCatalog;
    private final MemoryAssembler memoryAssembler;

    public CapabilityDagPlanner(LLMService llmService, CapabilityCatalog capabilityCatalog,
                                MemoryAssembler memoryAssembler) {
        this.llmService = llmService;
        this.capabilityCatalog = capabilityCatalog;
        this.memoryAssembler = memoryAssembler;
    }

    public DagPlan plan(Memory memory, String userMessage) {
        List<CapabilityDefinition> capabilities = capabilityCatalog.getEnabledCapabilities().stream()
                .sorted(Comparator.comparing(CapabilityDefinition::getName))
                .toList();

        List<Message> history = new ArrayList<>();
        MemoryContext memoryContext = memoryAssembler.assemble(memory, userMessage, MemoryUseCase.GRAPH_PLANNER);
        String context = memoryContext.toPromptText();
        if (context != null && !context.isEmpty()) {
            history.add(new Message("user", "[当前会话上下文]\n" + context));
        }

        String raw = llmService.chatStructured(
                buildSystemPrompt(capabilities),
                history,
                userMessage,
                "capability_dag",
                buildDagPlanSchema(capabilities));
        log.info("CapabilityDagPlanner raw: {}", raw);
        return JSON.parseObject(raw, DagPlan.class);
    }

    private String buildSystemPrompt(List<CapabilityDefinition> capabilities) {
        return """
                你是企业级DAG Agent Runtime中的CapabilityDagPlanner。
                你的职责是把用户目标拆解为抽象能力节点DAG，而不是直接选择具体工具。

                可用能力:
                %s

                规则:
                1. 只输出符合JSON Schema的DagPlan。
                2. capability必须逐字使用可用能力中的名称，不要编造能力。
                3. node_type必须与所选capability的nodeType一致。
                4. 第一版按拓扑串行执行设计，但仍需用depends_on表达节点依赖。
                5. 工具调用不是你的职责；需要业务能力时选择对应capability即可。
                6. 普通解释、总结、判断类任务使用reason.general或conversation.answer。
                7. 缺少必要信息时使用user.ask_clarification。
                8. 最后必须包含conversation.answer能力节点作为最终回复节点。
                """.formatted(buildCapabilitiesDescription(capabilities));
    }

    private String buildCapabilitiesDescription(List<CapabilityDefinition> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "当前没有可用能力。";
        }
        return capabilities.stream()
                .map(capability -> "- " + capability.getName()
                        + ": " + capability.getDescription()
                        + ", nodeType: " + capability.getNodeType()
                        + (capability.getDomain() != null ? ", domain: " + capability.getDomain() : "")
                        + (capability.getIntents() != null && !capability.getIntents().isEmpty()
                        ? ", intents: " + String.join("/", capability.getIntents()) : ""))
                .collect(Collectors.joining("\n"));
    }

    private String buildDagPlanSchema(List<CapabilityDefinition> capabilities) {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("dag_id", stringSchema());
        properties.put("goal", stringSchema());

        JSONObject nodes = new JSONObject();
        nodes.put("type", "array");
        nodes.put("minItems", 1);
        nodes.put("items", buildNodeSchema(capabilities));
        properties.put("nodes", nodes);

        JSONObject edges = new JSONObject();
        edges.put("type", "array");
        edges.put("items", buildEdgeSchema());
        properties.put("edges", edges);

        JSONObject metadata = new JSONObject();
        metadata.put("type", "object");
        metadata.put("additionalProperties", false);
        metadata.put("properties", new JSONObject());
        metadata.put("required", List.of());
        properties.put("metadata", metadata);

        root.put("properties", properties);
        root.put("required", List.of("dag_id", "goal", "nodes", "edges", "metadata"));
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
            case LLM_REASON -> "ReasonAgent";
            case ASK_USER -> "AskUserAgent";
            case FINAL_ANSWER -> "AnswerAgent";
            case VERIFY -> "VerifierAgent";
        };
    }

    private JSONObject enumStringSchema(String value) {
        JSONObject schema = stringSchema();
        schema.put("enum", List.of(value));
        return schema;
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }
}
