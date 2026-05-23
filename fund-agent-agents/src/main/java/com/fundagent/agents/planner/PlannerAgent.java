package com.fundagent.agents.planner;

import com.fundagent.common.model.Message;
import com.fundagent.core.agent.Agent;
import com.fundagent.core.agent.AgentDefinition;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.post.Post;
import com.fundagent.core.post.PostTranslator;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@AgentDefinition(name = "Planner", description = "任务规划器，分析用户意图并路由到合适的执行器", isPlanner = true)
public class PlannerAgent extends Agent {
    private final String systemPrompt;
    private final PostTranslator translator;
    private final int contextRounds;

    public PlannerAgent(AgentEntry entry, AgentRegistry agentRegistry, int contextRounds) {
        super(entry);
        this.systemPrompt = loadPrompt(agentRegistry);
        this.translator = new PostTranslator(agentRegistry, 3);
        this.contextRounds = contextRounds;
    }

    private String loadPrompt(AgentRegistry agentRegistry) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("prompts/planner-prompt.yaml")) {
            if (is != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                String template = (String) data.get("prompt");
                return template.replace("{workers_description}", agentRegistry.getWorkersDescription());
            }
        } catch (Exception e) {
            log.warn("Failed to load planner prompt file, using default");
        }
        return defaultPrompt(agentRegistry);
    }

    private String defaultPrompt(AgentRegistry agentRegistry) {
        return """
                你是一个资金系统智能助手的任务规划器。
                分析用户意图，拆解任务，决定由谁执行。

                可用执行器:
                """ + agentRegistry.getWorkersDescription() + """

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
                3. 回复用户 → send_to="User", stop=true
                4. 只输出纯JSON
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

        log.info("Planner processing: {}", incoming.getMessage());
        String rawResponse = onToken != null
                ? llmService.chatStream(systemPrompt, history, incoming.getMessage(), onToken)
                : llmService.chat(systemPrompt, history, incoming.getMessage());
        log.info("Planner raw: {}", rawResponse);

        Post post = translator.parse("Planner", rawResponse);
        log.info("Planner route → {}: {}", post.getSendTo(), post.getMessage());
        return post;
    }
}
