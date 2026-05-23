package com.fundagent.server.config;

import com.fundagent.agents.executor.ExecutorAgent;
import com.fundagent.agents.planner.PlannerAgent;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.llm.LLMConfig;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.llm.OpenAIService;
import com.fundagent.core.orchestration.Orchestrator;
import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Configuration
public class AgentAutoConfig {

    @Value("${agent.llm.api-base}")
    private String apiBase;
    @Value("${agent.llm.api-key}")
    private String apiKey;
    @Value("${agent.llm.model:gpt-4}")
    private String model;
    @Value("${agent.llm.temperature:0.1}")
    private double temperature;
    @Value("${agent.llm.max-tokens:2048}")
    private int maxTokens;
    @Value("${agent.llm.timeout-seconds:60}")
    private int timeoutSeconds;
    @Value("${agent.orchestration.max-internal-rounds:10}")
    private int maxInternalRounds;
    @Value("${agent.orchestration.context-rounds:10}")
    private int contextRounds;
    @Value("${agent.orchestration.executor-max-retries:3}")
    private int executorMaxRetries;

    @Bean
    public LLMConfig llmConfig() {
        LLMConfig config = new LLMConfig();
        config.setApiBase(apiBase);
        config.setApiKey(apiKey);
        config.setModel(model);
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        config.setTimeoutSeconds(timeoutSeconds);
        return config;
    }

    @Bean
    public LLMService llmService() {
        return new OpenAIService(llmConfig());
    }

    @Bean
    public ToolRegistry toolRegistry(ApplicationContext ctx) {
        ToolRegistry registry = new ToolRegistry();
        Map<String, Object> beans = ctx.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);
        for (Object bean : beans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool != null) {
                    ToolDefinition def = ToolDefinition.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .params(Arrays.asList(tool.params()))
                            .method(method)
                            .build();
                    registry.register(def, bean);
                    log.info("Registered tool: {}", tool.name());
                }
            }
        }
        return registry;
    }

    @Bean
    public AgentRegistry agentRegistry() {
        return new AgentRegistry();
    }

    @Bean
    public Orchestrator orchestrator(AgentRegistry registry) {
        return new Orchestrator(registry, maxInternalRounds);
    }

    @Bean
    public AgentEntry plannerEntry() {
        return new AgentEntry("Planner", "任务规划器", PlannerAgent.class, true);
    }

    @Bean
    public AgentEntry executorEntry() {
        return new AgentEntry("Executor", "工具调用执行器", ExecutorAgent.class, false);
    }

    @Bean
    public PlannerAgent plannerAgent(AgentRegistry registry) {
        PlannerAgent agent = new PlannerAgent(plannerEntry(), registry, contextRounds);
        agent.setLlmService(llmService());
        return agent;
    }

    @Bean
    public ExecutorAgent executorAgent(ToolRegistry toolRegistry) {
        ExecutorAgent agent = new ExecutorAgent(executorEntry(), toolRegistry, executorMaxRetries);
        agent.setLlmService(llmService());
        return agent;
    }

    @Bean
    public Object agentInitializer(AgentRegistry registry, PlannerAgent planner, ExecutorAgent executor) {
        registry.register(plannerEntry());
        registry.register(executorEntry());
        registry.registerInstance(planner);
        registry.registerInstance(executor);
        log.info("Agents registered: Planner, Executor");
        return new Object();
    }
}
