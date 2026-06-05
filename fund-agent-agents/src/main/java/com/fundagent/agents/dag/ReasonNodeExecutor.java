package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagGraphState;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.NodeExecutionStatus;
import com.fundagent.core.dag.NodeExecutor;
import com.fundagent.core.dag.NodeObservation;
import com.fundagent.core.dag.NodeType;
import com.fundagent.core.llm.LLMService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ReasonNodeExecutor implements NodeExecutor {
    private final LLMService llmService;
    private final String outputSchema;

    public ReasonNodeExecutor(LLMService llmService) {
        this.llmService = llmService;
        this.outputSchema = buildOutputSchema();
    }

    @Override
    public boolean supports(BoundDagNode node) {
        return node != null && NodeType.LLM_REASON.equals(node.getNodeType());
    }

    @Override
    public NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        long start = System.currentTimeMillis();
        if (!supports(node)) {
            NodeObservation observation = failedObservation(node, "UNSUPPORTED_NODE",
                    "ReasonNodeExecutor不支持当前节点", System.currentTimeMillis() - start);
            return NodeExecutionResult.failed(observation, "UNSUPPORTED_NODE", observation.getError());
        }

        String raw = llmService.chatStructured(
                buildSystemPrompt(),
                List.of(),
                buildCurrentMessage(node, state, context),
                "reason_node_output",
                outputSchema);
        log.info("ReasonNodeExecutor raw: nodeId={}, raw={}", node.getNodeId(), raw);
        ReasonNodeOutput output = JSON.parseObject(raw, ReasonNodeOutput.class);

        NodeObservation observation = NodeObservation.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .capability(node.getCapability())
                .status(NodeExecutionStatus.SUCCESS)
                .summary(output.getSummary())
                .outputs(parseOutputs(output.getOutputsJson()))
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
        return NodeExecutionResult.success(observation);
    }

    private String buildSystemPrompt() {
        return """
                你是企业级DAG Agent Runtime中的ReasonNodeExecutor。
                你的职责是根据用户原始问题、当前节点instruction和前序Observation，生成结构化中间分析结果。

                规则:
                1. 只输出符合JSON Schema的ReasonNodeOutput。
                2. 不调用工具，不编造外部系统结果。
                3. summary用一句话概括你的分析结论。
                4. outputs_json必须是一个JSON对象字符串，用于保存后续节点可引用的结构化字段。
                """;
    }

    private String buildCurrentMessage(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        JSONObject message = new JSONObject();
        message.put("user_message", context != null ? context.getUserMessage() : null);
        message.put("node", JSON.toJSON(node));
        message.put("previous_observations", state != null ? state.getObservations() : Map.of());
        return message.toJSONString();
    }

    private Map<String, Object> parseOutputs(String outputsJson) {
        Map<String, Object> outputs = new HashMap<>();
        if (outputsJson == null || outputsJson.isBlank()) {
            return outputs;
        }
        try {
            outputs.putAll(JSON.parseObject(outputsJson));
        } catch (Exception e) {
            outputs.put("raw_outputs_json", outputsJson);
        }
        return outputs;
    }

    private String buildOutputSchema() {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("summary", stringSchema());
        properties.put("outputs_json", stringSchema());

        root.put("properties", properties);
        root.put("required", List.of("summary", "outputs_json"));
        return root.toJSONString();
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }

    private NodeObservation failedObservation(BoundDagNode node, String errorCode, String error, long elapsedMs) {
        return NodeObservation.builder()
                .nodeId(node != null ? node.getNodeId() : null)
                .nodeType(node != null ? node.getNodeType() : null)
                .capability(node != null ? node.getCapability() : null)
                .status(NodeExecutionStatus.FAILED)
                .summary(error)
                .errorCode(errorCode)
                .error(error)
                .elapsedMs(elapsedMs)
                .build();
    }
}
