package com.fundagent.agents.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.graph.GraphState;
import com.fundagent.core.graph.Observation;
import com.fundagent.core.llm.LLMService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GraphAnswerGenerator {
    private final LLMService llmService;
    private final String systemPrompt;
    private final String answerSchema;

    public GraphAnswerGenerator(LLMService llmService) {
        this.llmService = llmService;
        this.systemPrompt = buildSystemPrompt();
        this.answerSchema = buildAnswerSchema();
    }

    public String generate(GraphState state) {
        String raw = llmService.chatStructured(
                systemPrompt,
                List.of(),
                buildUserMessage(state),
                "graph_final_answer",
                answerSchema);
        log.info("GraphAnswerGenerator raw: {}", raw);
        JSONObject json = JSON.parseObject(raw);
        return json.getString("answer");
    }

    private String buildSystemPrompt() {
        return """
                你是企业Agent的最终回复生成器。
                你的职责是把复杂任务的执行结果整理成用户能直接看懂的自然语言。

                规则:
                1. 不要输出JSON、代码块或内部Observation字段名。
                2. 不要暴露taskId、stepId、tool_name等内部实现细节。
                3. 如果工具执行成功，直接总结关键业务信息。
                4. 如果有文件链接或文件名，明确告诉用户已生成或已发送。
                5. 如果工具失败，说明失败原因并给出下一步建议。
                6. 回复要简洁、清楚、适合聊天窗口阅读。
                """;
    }

    private String buildUserMessage(GraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户原始问题:\n").append(state.getUserMessage()).append("\n\n");
        sb.append("任务目标:\n");
        if (state.getTaskPlan() != null) {
            sb.append(state.getTaskPlan().getGoal());
        }
        sb.append("\n\n执行结果:\n");
        for (Observation observation : state.getObservations().values()) {
            if ("FINAL_ANSWER".equals(observation.getSource())) continue;
            sb.append("- source=").append(observation.getSource())
                    .append(", success=").append(observation.isSuccess());
            if (observation.getData() != null) {
                sb.append(", data=").append(JSON.toJSONString(observation.getData()));
            }
            if (observation.getError() != null) {
                sb.append(", error=").append(observation.getError());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildAnswerSchema() {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        JSONObject answer = new JSONObject();
        answer.put("type", "string");
        properties.put("answer", answer);

        root.put("properties", properties);
        root.put("required", List.of("answer"));
        return root.toJSONString();
    }
}
