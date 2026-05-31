package com.fundagent.server.config;

import com.alibaba.fastjson2.JSON;
import com.fundagent.agents.executor.ExecutorAgent;
import com.fundagent.agents.graph.GraphAnswerGenerator;
import com.fundagent.agents.graph.GraphTaskPlanner;
import com.fundagent.agents.planner.PlannerAgent;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.llm.LLMConfig;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.llm.OpenAIService;
import com.fundagent.core.graph.GraphOrchestrator;
import com.fundagent.core.orchestration.Orchestrator;
import com.fundagent.core.routing.RuleBasedTaskRouter;
import com.fundagent.core.routing.TaskRouter;
import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
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
    public ToolRegistry toolRegistry(ConfigurableListableBeanFactory beanFactory) {
        ToolRegistry registry = new ToolRegistry();
        Map<String, Map<String, Object>> metadata = loadToolMetadata();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasToolMethod(beanType)) {
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            for (Method method : beanType.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool != null) {
                    Map<String, Object> toolMetadata = metadata.getOrDefault(tool.name(), Map.of());
                    ToolDefinition def = ToolDefinition.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .domain(asString(toolMetadata.get("domain")))
                            .version(asString(toolMetadata.get("version")))
                            .riskLevel(asString(toolMetadata.get("riskLevel")))
                            .enabled(asBoolean(toolMetadata.get("enabled"), true))
                            .params(Arrays.asList(tool.params()))
                            .paramsSchemaJson(toSchemaJson(toolMetadata.get("paramsSchema")))
                            .metadata(new HashMap<>(toolMetadata))
                            .method(method)
                            .build();
                    registry.register(def, bean);
                    log.info("Registered tool: {}", tool.name());
                }
            }
        }
        return registry;
    }

    private boolean hasToolMethod(Class<?> beanType) {
        for (Method method : beanType.getDeclaredMethods()) {
            if (method.getAnnotation(Tool.class) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadToolMetadata() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("tools.yaml")) {
            if (is == null) {
                log.info("tools.yaml not found, use @Tool annotation only");
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(is);
            Object tools = root != null ? root.get("tools") : null;
            if (tools instanceof Map<?, ?> map) {
                return (Map<String, Map<String, Object>>) map;
            }
        } catch (Exception e) {
            log.warn("Failed to load tools.yaml, use @Tool annotation only", e);
        }
        return Map.of();
    }

    private String toSchemaJson(Object schema) {
        return schema != null ? JSON.toJSONString(schema) : null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : defaultValue;
    }

    @Bean
    public TaskRouter taskRouter() {
        return new RuleBasedTaskRouter();
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
    public GraphOrchestrator graphOrchestrator(ToolRegistry toolRegistry, GraphAnswerGenerator graphAnswerGenerator) {
        GraphOrchestrator graphOrchestrator = new GraphOrchestrator(toolRegistry);
        graphOrchestrator.setAnswerGenerator(graphAnswerGenerator::generate);
        return graphOrchestrator;
    }

    @Bean
    public GraphTaskPlanner graphTaskPlanner(LLMService llmService, ToolRegistry toolRegistry) {
        return new GraphTaskPlanner(llmService, toolRegistry, contextRounds);
    }

    @Bean
    public GraphAnswerGenerator graphAnswerGenerator(LLMService llmService) {
        return new GraphAnswerGenerator(llmService);
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
    public PlannerAgent plannerAgent(AgentRegistry registry, ToolRegistry toolRegistry) {
        PlannerAgent agent = new PlannerAgent(plannerEntry(), registry, toolRegistry, contextRounds);
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
