package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.capability.CapabilityDefinition;
import com.fundagent.core.dag.NodeType;

import java.util.List;

public class DagPlanSchemaBuilder {

    public String build(List<CapabilityDefinition> capabilities) {
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
            case KNOWLEDGE_SEARCH -> "KnowledgeAgent";
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
