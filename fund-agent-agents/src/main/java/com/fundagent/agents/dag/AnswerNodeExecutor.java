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
public class AnswerNodeExecutor implements NodeExecutor {
    private final LLMService llmService;
    private final String answerSchema;

    public AnswerNodeExecutor(LLMService llmService) {
        this.llmService = llmService;
        this.answerSchema = buildAnswerSchema();
    }

    @Override
    public boolean supports(BoundDagNode node) {
        return node != null && NodeType.FINAL_ANSWER.equals(node.getNodeType());
    }

    @Override
    public NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        long start = System.currentTimeMillis();
        if (!supports(node)) {
            NodeObservation observation = failedObservation(node, "UNSUPPORTED_NODE",
                    "AnswerNodeExecutor不支持当前节点", System.currentTimeMillis() - start);
            return NodeExecutionResult.failed(observation, "UNSUPPORTED_NODE", observation.getError());
        }

        String raw = llmService.chatStructured(
                buildSystemPrompt(),
                List.of(),
                buildCurrentMessage(node, state, context),
                "answer_node_output",
                answerSchema);
        log.info("AnswerNodeExecutor raw: nodeId={}, raw={}", node.getNodeId(), raw);
        AnswerNodeOutput output = JSON.parseObject(raw, AnswerNodeOutput.class);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("answer", output.getAnswer());
        NodeObservation observation = NodeObservation.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .capability(node.getCapability())
                .status(NodeExecutionStatus.SUCCESS)
                .summary(output.getAnswer())
                .outputs(outputs)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
        return NodeExecutionResult.success(observation);
    }

    private String buildSystemPrompt() {
        return """
                你是企业级DAG Agent Runtime中的AnswerNodeExecutor。
                你的职责是根据用户原始问题、当前节点instruction和所有前序Observation，生成最终用户回复。

                规则:
                1. 只输出符合JSON Schema的AnswerNodeOutput。
                2. 不要暴露node_id、tool_name、Observation等内部字段名。
                3. 如果工具执行成功，直接总结关键业务信息。
                4. 如果前序节点失败或信息不足，说明原因并给出下一步建议。
                5. 回复要简洁、清楚、适合聊天窗口阅读。
                """;
    }

    private String buildCurrentMessage(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        JSONObject message = new JSONObject();
        message.put("user_message", context != null ? context.getUserMessage() : null);
        message.put("node", JSON.toJSON(node));
        message.put("previous_observations", state != null ? state.getObservations() : Map.of());
        return message.toJSONString();
    }

    private String buildAnswerSchema() {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("answer", stringSchema());

        root.put("properties", properties);
        root.put("required", List.of("answer"));
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
