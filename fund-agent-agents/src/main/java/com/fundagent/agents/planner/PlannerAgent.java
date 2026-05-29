package com.fundagent.agents.planner;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.agent.Agent;
import com.fundagent.core.agent.AgentDefinition;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.plan.Plan;
import com.fundagent.core.plan.PlanValidationResult;
import com.fundagent.core.plan.PlanValidator;
import com.fundagent.core.post.Post;
import com.fundagent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@AgentDefinition(name = "Planner", description = "任务规划器，分析用户意图并路由到合适的执行器", isPlanner = true)
public class PlannerAgent extends Agent {
    private final String systemPrompt;
    private final String planSchema;
    private final PlanValidator planValidator;
    private final int contextRounds;

    public PlannerAgent(AgentEntry entry, AgentRegistry agentRegistry,
                        ToolRegistry toolRegistry, int contextRounds) {
        super(entry);
        this.systemPrompt = loadPrompt(agentRegistry, toolRegistry);
        this.planSchema = buildPlanSchema(toolRegistry);
        this.planValidator = new PlanValidator(toolRegistry);
        this.contextRounds = contextRounds;
    }

    private String loadPrompt(AgentRegistry agentRegistry, ToolRegistry toolRegistry) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("prompts/planner-prompt.yaml")) {
            if (is != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                String template = (String) data.get("prompt");
                return template
                        .replace("{workers_description}", agentRegistry.getWorkersDescription())
                        .replace("{tools_description}", toolRegistry.getToolsDescription());
            }
        } catch (Exception e) {
            log.warn("Failed to load planner prompt file, using default");
        }
        return defaultPrompt(agentRegistry, toolRegistry);
    }

    private String defaultPrompt(AgentRegistry agentRegistry, ToolRegistry toolRegistry) {
        return """
                你是一个资金系统智能助手的任务规划器。
                分析用户意图，拆解任务，决定由谁执行。

                可用执行器:
                """ + agentRegistry.getWorkersDescription() + """

                可用工具:
                """ + toolRegistry.getToolsDescription() + """

                输出JSON:
                {
                  "plan_reasoning": "推理过程",
                  "send_to": "Executor 或 User",
                  "message": "指令或回复",
                  "stop": false
                }

                规则:
                1. send_to只能是"Executor"或"User"
                2. 需要调工具 → send_to="Executor"
                3. tool_name必须逐字使用可用工具中的英文name，tool_params必须逐字使用可用工具中的英文参数名
                4. 回复用户 → send_to="User", stop=true
                5. 只输出纯JSON
                """;
    }

    @Override
    public Post reply(Memory memory, Post incoming) {
        return reply(memory, incoming, null);
    }

    @Override
    public Post reply(Memory memory, Post incoming, Consumer<String> onToken) {
        String context = memory.toPromptContext(contextRounds);

        List<Message> history = new ArrayList<>();
        if (!context.isEmpty()) {
            history.add(new Message("user", context));
        }

        log.info("Planner processing: {}, totalRounds={}, contextLength={}",
                incoming.getMessage(), memory.getTotalRounds(), context.length());
        if (log.isDebugEnabled()) {
            log.debug("Planner context: {}", context);
        }
        String rawResponse = llmService.chatStructured(
                systemPrompt,
                history,
                incoming.getMessage(),
                "planner_plan",
                planSchema);
        log.info("Planner raw: {}", rawResponse);

        Plan plan;
        try {
            plan = JSON.parseObject(rawResponse, Plan.class);
        } catch (Exception e) {
            log.error("Planner plan parse failed: raw={}", rawResponse, e);
            PlanValidationResult validation = PlanValidationResult.error(
                    "PLAN_PARSE_ERROR", "Planner输出不是合法Plan JSON", false);
            return invalidPlanPost(validation);
        }

        PlanValidationResult validation = planValidator.validate(plan);
        if (!validation.isValid()) {
            log.error("Planner plan validation failed: code={}, message={}, raw={}",
                    validation.getErrorCode(), validation.getMessage(), rawResponse);
            return invalidPlanPost(validation);
        }

        Post post = toPost(plan);
        log.info("Planner route → {}: {}", post.getSendTo(), post.getMessage());
        return post;
    }

    private Post toPost(Plan plan) {
        Post post = Post.create("Planner", plan.getSendTo(), plan.getMessage() != null ? plan.getMessage() : "");
        post.setTimestamp(System.currentTimeMillis());
        if (plan.getPlanReasoning() != null && !plan.getPlanReasoning().isEmpty()) {
            post.addAttachment("plan", plan.getPlanReasoning());
        }
        if ("Executor".equals(plan.getSendTo())) {
            post.addAttachment("tool_name", plan.getToolName());
            post.addAttachment("tool_params", JSON.toJSONString(plan.getToolParams()));
        }
        return post;
    }

    private Post invalidPlanPost(PlanValidationResult validation) {
        String message = validation.isRecoverable()
                ? "请补充必要信息：" + validation.getMessage()
                : "系统内部错误：Planner输出不符合协议。";
        Post post = Post.create("Planner", "User", message);
        post.addAttachment("plan_validation_error", validation.getErrorCode() + ": " + validation.getMessage());
        return post;
    }

    private String buildPlanSchema(ToolRegistry toolRegistry) {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        properties.put("plan_reasoning", stringSchema());

        JSONObject sendTo = stringSchema();
        sendTo.put("enum", List.of("Executor", "User"));
        properties.put("send_to", sendTo);

        properties.put("message", stringSchema());

        JSONObject toolName = stringSchema();
        JSONArray toolNameEnum = new JSONArray();
        toolNameEnum.add("");
        toolRegistry.getAllTools().forEach(tool -> toolNameEnum.add(tool.getName()));
        toolName.put("enum", toolNameEnum);
        properties.put("tool_name", toolName);

        JSONObject toolParams = new JSONObject();
        toolParams.put("type", "object");
        toolParams.put("additionalProperties", false);

        JSONObject paramProperties = new JSONObject();
        Set<String> paramNames = new LinkedHashSet<>();
        toolRegistry.getAllTools().forEach(tool -> paramNames.addAll(tool.getParams()));
        for (String paramName : paramNames) {
            paramProperties.put(paramName, stringSchema());
        }
        toolParams.put("properties", paramProperties);
        properties.put("tool_params", toolParams);

        JSONObject stop = new JSONObject();
        stop.put("type", "boolean");
        properties.put("stop", stop);

        root.put("properties", properties);
        root.put("required", List.of("plan_reasoning", "send_to", "message",
                "tool_name", "tool_params", "stop"));
        return root.toJSONString();
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        return schema;
    }
}
