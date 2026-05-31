package com.fundagent.agents.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.graph.TaskPlan;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class GraphTaskPlanner {
    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final int contextRounds;
    private final String systemPrompt;
    private final String taskPlanSchema;

    public GraphTaskPlanner(LLMService llmService, ToolRegistry toolRegistry, int contextRounds) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.contextRounds = contextRounds;
        this.systemPrompt = buildSystemPrompt(toolRegistry);
        this.taskPlanSchema = buildTaskPlanSchema(toolRegistry);
    }

    public TaskPlan plan(Memory memory, String userMessage) {
        List<Message> history = new ArrayList<>();
        String context = memory != null ? memory.toPromptContext(contextRounds) : "";
        if (context != null && !context.isEmpty()) {
            history.add(new Message("user", "[当前会话上下文]\n" + context));
        }

        String raw = llmService.chatStructured(
                systemPrompt,
                history,
                userMessage,
                "task_plan",
                taskPlanSchema);
        log.info("GraphTaskPlanner raw: {}", raw);
        return JSON.parseObject(raw, TaskPlan.class);
    }

    private String buildSystemPrompt(ToolRegistry toolRegistry) {
        return """
                你是企业级Agent系统中的GraphTaskPlanner。
                你的职责是把复杂用户任务拆解成可顺序执行的TaskPlan。

                可用工具:
                %s

                输出要求:
                1. 只输出符合JSON Schema的TaskPlan。
                2. steps按第一版顺序执行，不要设计并行分支。
                3. 每个TOOL_CALL步骤只能调用一个工具，tool_name必须逐字使用可用工具中的英文名。
                4. tool_params的key必须逐字使用工具参数名。
                5. 如果需要用户补充信息，生成ASK_USER步骤并停止后续无依据的工具调用。
                6. 最后必须生成FINAL_ANSWER步骤，用于根据前面Observation汇总最终回复。
                7. 不要编造工具，不要编造工具返回结果。
                8. task_id可以为空字符串，由系统补齐。

                示例:
                用户: 查询EC2025，如果已付款就发送回单，并告诉我结果
                steps:
                - TOOL_CALL queryPaymentDocuments {"noteCode":"EC2025"}
                - TOOL_CALL sendReceiptFiles {"noteCode":"EC2025"}
                - FINAL_ANSWER 根据查询和发送结果汇总给用户
                """.formatted(toolRegistry.getToolsDescription());
    }

    private String buildTaskPlanSchema(ToolRegistry toolRegistry) {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("task_id", stringSchema());
        properties.put("goal", stringSchema());

        JSONObject steps = new JSONObject();
        steps.put("type", "array");
        steps.put("minItems", 1);
        steps.put("items", buildStepSchema(toolRegistry));
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

    private JSONObject buildStepSchema(ToolRegistry toolRegistry) {
        JSONObject step = new JSONObject();
        step.put("type", "object");
        step.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("step_id", stringSchema());
        properties.put("name", stringSchema());

        JSONObject type = stringSchema();
        type.put("enum", List.of("TOOL_CALL", "ASK_USER", "FINAL_ANSWER"));
        properties.put("type", type);

        JSONObject dependsOn = new JSONObject();
        dependsOn.put("type", "array");
        dependsOn.put("items", stringSchema());
        properties.put("depends_on", dependsOn);

        JSONObject toolName = stringSchema();
        JSONArray toolNameEnum = new JSONArray();
        toolNameEnum.add("");
        toolRegistry.getAllTools().stream()
                .filter(ToolDefinition::isEnabled)
                .forEach(tool -> toolNameEnum.add(tool.getName()));
        toolName.put("enum", toolNameEnum);
        properties.put("tool_name", toolName);

        ToolParamSchema toolParamSchema = buildToolParamProperties(toolRegistry);
        properties.put("tool_params", buildToolParamsSchema(toolParamSchema.properties(), toolParamSchema.paramNames()));

        properties.put("instruction", stringSchema());

        step.put("properties", properties);
        step.put("required", List.of("step_id", "name", "type", "depends_on",
                "tool_name", "tool_params", "instruction"));
        return step;
    }

    private ToolParamSchema buildToolParamProperties(ToolRegistry toolRegistry) {
        JSONObject paramProperties = new JSONObject();
        Set<String> paramNames = new LinkedHashSet<>();
        for (ToolDefinition tool : toolRegistry.getAllTools()) {
            if (tool.getParamsSchemaJson() != null && !tool.getParamsSchemaJson().isEmpty()) {
                JSONObject schema = JSON.parseObject(tool.getParamsSchemaJson());
                JSONObject schemaProperties = schema.getJSONObject("properties");
                if (schemaProperties != null) {
                    for (String paramName : schemaProperties.keySet()) {
                        paramProperties.put(paramName, schemaProperties.getJSONObject(paramName));
                        paramNames.add(paramName);
                    }
                }
            }
            paramNames.addAll(tool.getParams());
        }
        for (String paramName : paramNames) {
            if (!paramProperties.containsKey(paramName)) {
                paramProperties.put(paramName, stringSchema());
            }
        }
        return new ToolParamSchema(paramProperties, paramNames);
    }

    private JSONObject buildToolParamsSchema(JSONObject paramProperties, Set<String> paramNames) {
        JSONObject emptyParams = new JSONObject();
        emptyParams.put("type", "object");
        emptyParams.put("additionalProperties", false);
        emptyParams.put("properties", new JSONObject());
        emptyParams.put("required", List.of());

        JSONObject paramsWithValues = new JSONObject();
        paramsWithValues.put("type", "object");
        paramsWithValues.put("additionalProperties", false);
        paramsWithValues.put("properties", paramProperties);
        paramsWithValues.put("required", new ArrayList<>(paramNames));

        JSONObject schema = new JSONObject();
        schema.put("anyOf", List.of(emptyParams, paramsWithValues));
        return schema;
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }

    private record ToolParamSchema(JSONObject properties, Set<String> paramNames) {
    }
}
