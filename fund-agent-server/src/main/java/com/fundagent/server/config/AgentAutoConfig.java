package com.fundagent.server.config;

import com.fundagent.agents.executor.ExecutorAgent;
import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.agents.dag.CapabilityPlanningContextProvider;
import com.fundagent.agents.dag.DagPlanSchemaBuilder;
import com.fundagent.agents.graph.GraphAnswerGenerator;
import com.fundagent.agents.graph.GraphTaskPlanner;
import com.fundagent.agents.planner.PlannerAgent;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityCatalogProvider;
import com.fundagent.core.capability.CapabilityValidator;
import com.fundagent.core.dag.DefaultToolBinder;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.llm.LLMConfig;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.llm.OpenAIService;
import com.fundagent.core.graph.GraphOrchestrator;
import com.fundagent.core.memory.DefaultMemoryAssembler;
import com.fundagent.core.memory.MemoryAssembler;
import com.fundagent.core.orchestration.Orchestrator;
import com.fundagent.core.routing.RuleBasedTaskRouter;
import com.fundagent.core.routing.TaskRouter;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.catalog.ToolCatalog;
import com.fundagent.core.tool.catalog.ToolCatalogProvider;
import com.fundagent.core.tool.provider.ToolProvider;
import com.fundagent.core.tool.schema.ToolSchemaResolver;
import com.fundagent.core.tool.selection.DefaultToolSelector;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import com.fundagent.core.tool.selection.ToolSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
    public ToolRegistry toolRegistry(List<ToolProvider> providers) {
        log.info("ToolRegistry initialized: providers={}", providers.size());
        return new ToolRegistry(providers);
    }

    @Bean
    public ToolCatalog toolCatalog(List<ToolCatalogProvider> providers) {
        ToolCatalog catalog = ToolCatalog.fromProviders(providers);
        log.info("ToolCatalog initialized: tools={}", catalog.getAllTools().size());
        return catalog;
    }

    @Bean
    public ToolSelector toolSelector(List<ToolSelectionStage> stages) {
        log.info("ToolSelector initialized: stages={}", stages.size());
        return new DefaultToolSelector(stages);
    }

    @Bean
    public ToolSchemaResolver toolSchemaResolver(ToolRegistry toolRegistry) {
        return new ToolSchemaResolver(toolRegistry);
    }

    @Bean
    public CapabilityCatalog capabilityCatalog(List<CapabilityCatalogProvider> providers) {
        CapabilityCatalog catalog = CapabilityCatalog.fromProviders(providers);
        log.info("CapabilityCatalog initialized: capabilities={}", catalog.getAllCapabilities().size());
        return catalog;
    }

    @Bean
    public CapabilityValidator capabilityValidator(CapabilityCatalog capabilityCatalog) {
        return new CapabilityValidator(capabilityCatalog);
    }

    @Bean
    public DagPlanValidator dagPlanValidator(CapabilityCatalog capabilityCatalog) {
        return new DagPlanValidator(capabilityCatalog);
    }

    @Bean
    public ToolBinder toolBinder(CapabilityCatalog capabilityCatalog, ToolSchemaResolver toolSchemaResolver) {
        return new DefaultToolBinder(capabilityCatalog, toolSchemaResolver);
    }

    @Bean
    public DagPlanSchemaBuilder dagPlanSchemaBuilder() {
        return new DagPlanSchemaBuilder();
    }

    @Bean
    public CapabilityPlanningContextProvider capabilityPlanningContextProvider(
            CapabilityCatalog capabilityCatalog,
            DagPlanSchemaBuilder dagPlanSchemaBuilder) {
        return new CapabilityPlanningContextProvider(capabilityCatalog, dagPlanSchemaBuilder);
    }

    @Bean
    public TaskRouter taskRouter() {
        return new RuleBasedTaskRouter();
    }

    @Bean
    public MemoryAssembler memoryAssembler() {
        return new DefaultMemoryAssembler(contextRounds);
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
    public GraphTaskPlanner graphTaskPlanner(LLMService llmService, ToolRegistry toolRegistry,
                                             MemoryAssembler memoryAssembler) {
        return new GraphTaskPlanner(llmService, toolRegistry, memoryAssembler);
    }

    @Bean
    public CapabilityDagPlanner capabilityDagPlanner(LLMService llmService,
                                                     CapabilityPlanningContextProvider planningContextProvider,
                                                     MemoryAssembler memoryAssembler) {
        return new CapabilityDagPlanner(llmService, planningContextProvider, memoryAssembler);
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
    public PlannerAgent plannerAgent(AgentRegistry registry, ToolRegistry toolRegistry,
                                     MemoryAssembler memoryAssembler) {
        PlannerAgent agent = new PlannerAgent(plannerEntry(), registry, toolRegistry, memoryAssembler);
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
