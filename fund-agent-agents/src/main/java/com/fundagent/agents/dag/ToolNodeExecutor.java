package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.BoundTool;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagGraphState;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.NodeExecutionStatus;
import com.fundagent.core.dag.NodeExecutor;
import com.fundagent.core.dag.NodeObservation;
import com.fundagent.core.dag.NodeType;
import com.fundagent.core.dag.ToolBindingStatus;
import com.fundagent.core.dag.ToolCallRecord;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ToolNodeExecutor implements NodeExecutor {
    private final LLMService llmService;
    private final ToolRegistry toolRegistry;

    public ToolNodeExecutor(LLMService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public boolean supports(BoundDagNode node) {
        if (node == null) {
            return false;
        }
        return ToolBindingStatus.BOUND.equals(node.getBindingStatus())
                && (NodeType.QUERY.equals(node.getNodeType())
                || NodeType.ACTION.equals(node.getNodeType())
                || NodeType.KNOWLEDGE_SEARCH.equals(node.getNodeType()));
    }

    @Override
    public NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        long start = System.currentTimeMillis();
        if (!supports(node)) {
            NodeObservation observation = failedObservation(node, "UNSUPPORTED_NODE",
                    "ToolNodeExecutor不支持当前节点", System.currentTimeMillis() - start);
            return NodeExecutionResult.failed(observation, "UNSUPPORTED_NODE", observation.getError());
        }

        ToolNodeSelectionDecision decision = decideToolSelection(node, state, context);
        if (!decision.isShouldCallTool()) {
            NodeObservation observation = NodeObservation.builder()
                    .nodeId(node.getNodeId())
                    .nodeType(node.getNodeType())
                    .capability(node.getCapability())
                    .status(NodeExecutionStatus.SKIPPED)
                    .summary(decision.getSkipReason())
                    .outputs(Map.of("rationale", safe(decision.getRationale())))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
            return NodeExecutionResult.success(observation);
        }

        String toolName = decision.getToolName();
        BoundTool selectedTool = findBoundTool(node, toolName);
        if (selectedTool == null) {
            NodeObservation observation = failedObservation(node, "UNBOUND_TOOL",
                    "LLM选择了未绑定工具: " + toolName, System.currentTimeMillis() - start);
            return NodeExecutionResult.failed(observation, "UNBOUND_TOOL", observation.getError());
        }

        Map<String, Object> args = decideToolParams(node, selectedTool, state, context, decision);
        ToolResult toolResult = toolRegistry.execute(toolName, args);
        ToolCallRecord callRecord = ToolCallRecord.builder()
                .toolName(toolName)
                .args(args)
                .success(toolResult.isSuccess())
                .data(toolResult.getData())
                .error(toolResult.getError())
                .errorCode(toolResult.getErrorCode())
                .build();

        NodeObservation observation = NodeObservation.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .capability(node.getCapability())
                .status(toolResult.isSuccess() ? NodeExecutionStatus.SUCCESS : NodeExecutionStatus.FAILED)
                .summary(toolResult.isSuccess() ? "工具调用成功" : "工具调用失败: " + toolResult.getError())
                .outputs(buildToolOutputs(toolName, toolResult.getData(), decision.getRationale()))
                .toolCalls(List.of(callRecord))
                .errorCode(toolResult.getErrorCode())
                .error(toolResult.getError())
                .elapsedMs(System.currentTimeMillis() - start)
                .build();

        if (!toolResult.isSuccess()) {
            return NodeExecutionResult.failed(observation, toolResult.getErrorCode(), toolResult.getError());
        }
        return NodeExecutionResult.success(observation);
    }

    private ToolNodeSelectionDecision decideToolSelection(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        String raw = llmService.chatStructured(
                buildSystemPrompt(node),
                List.of(),
                buildCurrentMessage(node, state, context),
                "tool_node_selection",
                buildSelectionSchema(node));
        log.info("ToolNodeExecutor selection raw: nodeId={}, raw={}", node.getNodeId(), raw);
        return JSON.parseObject(raw, ToolNodeSelectionDecision.class);
    }

    private Map<String, Object> decideToolParams(BoundDagNode node, BoundTool selectedTool, DagGraphState state,
                                                 DagExecutionContext context, ToolNodeSelectionDecision selection) {
        String raw = llmService.chatStructured(
                buildToolParamsSystemPrompt(node, selectedTool, selection),
                List.of(),
                buildCurrentMessage(node, state, context),
                selectedTool.getToolName() + "_params",
                buildToolParamsSchema(selectedTool).toJSONString());
        log.info("ToolNodeExecutor params raw: nodeId={}, tool={}, raw={}",
                node.getNodeId(), selectedTool.getToolName(), raw);
        return JSON.parseObject(raw);
    }

    private String buildSystemPrompt(BoundDagNode node) {
        return """
                你是企业级DAG Agent Runtime中的ToolNodeExecutor。
                你的职责是根据当前DAG节点、用户原始问题、前序Observation和绑定工具，决定是否调用一个工具。

                规则:
                1. 只输出符合JSON Schema的ToolNodeSelectionDecision。
                2. 如果节点目标需要外部数据或外部动作，should_call_tool=true。
                3. tool_name必须逐字使用当前节点bound_tools中的工具名。
                4. 如果根据前序Observation可判断当前节点不应执行，should_call_tool=false，并说明skip_reason。
                5. 不要编造工具结果；你只负责选择是否调用工具，不负责生成工具参数。

                当前节点:
                node_id: %s
                node_type: %s
                capability: %s
                instruction: %s

                绑定工具:
                %s
                """.formatted(
                node.getNodeId(),
                node.getNodeType(),
                node.getCapability(),
                node.getInstruction(),
                buildBoundToolsDescription(node));
    }

    private String buildToolParamsSystemPrompt(BoundDagNode node, BoundTool selectedTool,
                                               ToolNodeSelectionDecision selection) {
        return """
                你是企业级DAG Agent Runtime中的ToolParamsGenerator。
                你的职责是为已经选定的工具生成参数。

                规则:
                1. 只输出符合JSON Schema的工具参数对象。
                2. 参数key必须逐字匹配工具参数schema。
                3. 不要输出tool_name、解释文本或额外字段。
                4. 参数值只能来自用户原始问题、当前节点instruction、前序Observation和工具选择理由。

                当前节点:
                node_id: %s
                node_type: %s
                capability: %s
                instruction: %s

                已选工具:
                tool_name: %s
                description: %s
                params: %s
                rationale: %s
                """.formatted(
                node.getNodeId(),
                node.getNodeType(),
                node.getCapability(),
                node.getInstruction(),
                selectedTool.getToolName(),
                selectedTool.getDescription(),
                selectedTool.getParams(),
                selection.getRationale());
    }

    private String buildCurrentMessage(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        JSONObject message = new JSONObject();
        message.put("user_message", context != null ? context.getUserMessage() : null);
        message.put("node", JSON.toJSON(node));
        message.put("previous_observations", state != null ? state.getObservations() : Map.of());
        return message.toJSONString();
    }

    private String buildBoundToolsDescription(BoundDagNode node) {
        if (node.getBoundTools() == null || node.getBoundTools().isEmpty()) {
            return "当前节点没有绑定工具。";
        }
        StringBuilder sb = new StringBuilder();
        for (BoundTool tool : node.getBoundTools()) {
            sb.append("- ").append(tool.getToolName())
                    .append(": ").append(tool.getDescription())
                    .append(", provider: ").append(tool.getProviderType())
                    .append(", risk: ").append(tool.getRiskLevel())
                    .append(", params: ").append(tool.getParams())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildSelectionSchema(BoundDagNode node) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("should_call_tool", booleanSchema());
        properties.put("tool_name", toolNameSchema(node));
        properties.put("skip_reason", stringSchema());
        properties.put("rationale", stringSchema());

        schema.put("properties", properties);
        schema.put("required", List.of("should_call_tool", "tool_name", "skip_reason", "rationale"));
        return schema.toJSONString();
    }

    private JSONObject buildToolParamsSchema(BoundTool tool) {
        if (tool.getParamsSchemaJson() != null && !tool.getParamsSchemaJson().isBlank()) {
            return JSON.parseObject(tool.getParamsSchemaJson());
        }

        JSONObject params = emptyObjectSchema();
        JSONObject properties = params.getJSONObject("properties");
        List<String> paramNames = tool.getParams() != null ? tool.getParams() : List.of();
        for (String paramName : paramNames) {
            properties.put(paramName, stringSchema());
        }
        params.put("required", paramNames);
        return params;
    }

    private JSONObject booleanSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "boolean");
        return schema;
    }

    private JSONObject toolNameSchema(BoundDagNode node) {
        JSONObject schema = stringSchema();
        List<String> toolNames = node.getBoundTools() != null
                ? node.getBoundTools().stream().map(BoundTool::getToolName).toList()
                : List.of();
        java.util.ArrayList<String> allowedNames = new java.util.ArrayList<>(toolNames);
        allowedNames.add("");
        schema.put("enum", allowedNames);
        return schema;
    }

    private JSONObject emptyObjectSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", new JSONObject());
        schema.put("required", List.of());
        return schema;
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }

    private BoundTool findBoundTool(BoundDagNode node, String toolName) {
        if (toolName == null || node.getBoundTools() == null) {
            return null;
        }
        return node.getBoundTools().stream()
                .filter(tool -> toolName.equals(tool.getToolName()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildToolOutputs(String toolName, Object toolResult, String rationale) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("tool_name", toolName);
        outputs.put("tool_result", toolResult);
        outputs.put("rationale", safe(rationale));
        return outputs;
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

    private String safe(String value) {
        return value != null ? value : "";
    }
}
