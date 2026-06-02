package com.fundagent.agents.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.graph.TaskPlan;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.MemoryAssembler;
import com.fundagent.core.memory.MemoryContext;
import com.fundagent.core.memory.MemoryUseCase;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class GraphTaskPlanner {
    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final MemoryAssembler memoryAssembler;

    public GraphTaskPlanner(LLMService llmService, ToolRegistry toolRegistry, MemoryAssembler memoryAssembler) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.memoryAssembler = memoryAssembler;
    }

    public TaskPlan plan(Memory memory, String userMessage) {
        return plan(memory, userMessage, toolRegistry.getAllTools());
    }

    public TaskPlan plan(Memory memory, String userMessage, List<ToolDefinition> candidateTools) {
        List<ToolDefinition> enabledTools = normalizeTools(candidateTools);
        List<Message> history = new ArrayList<>();
        MemoryContext memoryContext = memoryAssembler.assemble(memory, userMessage, MemoryUseCase.GRAPH_PLANNER);
        String context = memoryContext.toPromptText();
        if (context != null && !context.isEmpty()) {
            history.add(new Message("user", "[当前会话上下文]\n" + context));
        }

        String raw = llmService.chatStructured(
                buildSystemPrompt(enabledTools),
                history,
                userMessage,
                "task_plan",
                buildTaskPlanSchema(enabledTools));
        log.info("GraphTaskPlanner raw: {}", raw);
        return JSON.parseObject(raw, TaskPlan.class);
    }

    private List<ToolDefinition> normalizeTools(List<ToolDefinition> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .filter(ToolDefinition::isEnabled)
                .sorted(Comparator.comparing(ToolDefinition::getName))
                .toList();
    }

    private String buildSystemPrompt(List<ToolDefinition> tools) {
        return """
                你是企业级Agent系统中的GraphTaskPlanner。
                你的职责是把复杂用户任务拆解成可顺序执行的TaskPlan。

                可用候选工具:
                %s

                输出要求:
                1. 只输出符合JSON Schema的TaskPlan。
                2. steps按第一版顺序执行，不要设计并行分支。
                3. 每个TOOL_CALL步骤只能调用一个工具，tool_name必须逐字使用候选工具中的英文名。
                4. tool_params的key必须逐字使用该工具自己的参数名。
                5. 如果候选工具不足以完成任务，先生成ASK_USER步骤说明缺少的信息或工具，并保留FINAL_ANSWER步骤作为最终回复占位。
                6. 如果需要用户补充信息，生成ASK_USER步骤并停止后续无依据的工具调用。
                7. 最后必须生成FINAL_ANSWER步骤，用于根据前面Observation汇总最终回复。
                8. 不要编造工具，不要编造工具返回结果。
                9. FINAL_ANSWER不能是第一个步骤。
                10. task_id可以为空字符串，由系统补齐。

                示例:
                用户: 查询EC2025，如果已付款就发送回单，并告诉我结果
                steps:
                - TOOL_CALL queryPaymentDocuments {"noteCode":"EC2025"}
                - TOOL_CALL sendReceiptFiles {"noteCode":"EC2025"}
                - FINAL_ANSWER 根据查询和发送结果汇总给用户
                """.formatted(buildToolsDescription(tools));
    }

    private String buildToolsDescription(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "当前没有可用候选工具。";
        }
        return tools.stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription()
                        + (t.getDomain() != null ? ", domain: " + t.getDomain() : "")
                        + (t.getIntents() != null && !t.getIntents().isEmpty()
                        ? ", intents: " + String.join("/", t.getIntents()) : "")
                        + (t.getRiskLevel() != null ? ", risk: " + t.getRiskLevel() : "")
                        + ", 参数: " + String.join(", ", safeParams(t)))
                .collect(Collectors.joining("\n"));
    }

    private List<String> safeParams(ToolDefinition tool) {
        return tool.getParams() != null ? tool.getParams() : List.of();
    }

    private String buildTaskPlanSchema(List<ToolDefinition> tools) {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("task_id", stringSchema());
        properties.put("goal", stringSchema());

        JSONObject steps = new JSONObject();
        steps.put("type", "array");
        steps.put("minItems", 1);
        steps.put("items", buildStepSchema(tools));
        properties.put("steps", steps);

        JSONObject metadata = new JSONObject();
        metadata.put("type", "object");
        metadata.put("additionalProperties", false);
        metadata.put("properties", new JSONObject());
        metadata.put("required", List.of());
        properties.put("metadata", metadata);

        root.put("properties", properties);
        root.put("required", List.of("task_id", "goal", "steps", "metadata"));
        return root.toJSONString();
    }

    private JSONObject buildStepSchema(List<ToolDefinition> tools) {
        JSONObject schema = new JSONObject();
        JSONArray branches = new JSONArray();
        tools.forEach(tool -> branches.add(buildToolCallStepSchema(tool)));
        branches.add(buildNonToolStepSchema("ASK_USER"));
        branches.add(buildNonToolStepSchema("FINAL_ANSWER"));
        schema.put("anyOf", branches);
        return schema;
    }

    private JSONObject buildToolCallStepSchema(ToolDefinition tool) {
        JSONObject step = baseStepObject();
        JSONObject properties = step.getJSONObject("properties");
        properties.put("type", enumStringSchema("TOOL_CALL"));
        properties.put("tool_name", enumStringSchema(tool.getName()));
        properties.put("tool_params", buildToolParamsSchema(tool));
        return step;
    }

    private JSONObject buildNonToolStepSchema(String stepType) {
        JSONObject step = baseStepObject();
        JSONObject properties = step.getJSONObject("properties");
        properties.put("type", enumStringSchema(stepType));
        properties.put("tool_name", enumStringSchema(""));
        properties.put("tool_params", emptyObjectSchema());
        return step;
    }

    private JSONObject baseStepObject() {
        JSONObject step = new JSONObject();
        step.put("type", "object");
        step.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("step_id", stringSchema());
        properties.put("name", stringSchema());

        JSONObject dependsOn = new JSONObject();
        dependsOn.put("type", "array");
        dependsOn.put("items", stringSchema());
        properties.put("depends_on", dependsOn);

        properties.put("instruction", stringSchema());
        step.put("properties", properties);
        step.put("required", List.of("step_id", "name", "type", "depends_on",
                "tool_name", "tool_params", "instruction"));
        return step;
    }

    private JSONObject buildToolParamsSchema(ToolDefinition tool) {
        if (tool.getParamsSchemaJson() != null && !tool.getParamsSchemaJson().isEmpty()) {
            return JSON.parseObject(tool.getParamsSchemaJson());
        }

        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        List<String> paramNames = safeParams(tool);
        for (String paramName : paramNames) {
            properties.put(paramName, stringSchema());
        }
        params.put("properties", properties);
        params.put("required", new ArrayList<>(paramNames));
        return params;
    }

    private JSONObject emptyObjectSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", new JSONObject());
        schema.put("required", List.of());
        return schema;
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
