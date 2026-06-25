package com.fundagent.server.config;

import com.fundagent.agents.dag.AnswerNodeExecutor;
import com.fundagent.agents.dag.AskUserNodeExecutor;
import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.agents.dag.CapabilityDagRePlanner;
import com.fundagent.agents.dag.CapabilityPlanningContextProvider;
import com.fundagent.agents.dag.DagPlanSchemaBuilder;
import com.fundagent.agents.dag.ReasonNodeExecutor;
import com.fundagent.agents.dag.ReplanningDagRuntime;
import com.fundagent.agents.dag.ToolNodeExecutor;
import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityCatalogProvider;
import com.fundagent.core.capability.CapabilityValidator;
import com.fundagent.core.dag.DefaultNodeRouter;
import com.fundagent.core.dag.DefaultNodeCompletionChecker;
import com.fundagent.core.dag.DefaultFinalDagVerifier;
import com.fundagent.core.dag.DefaultToolBinder;
import com.fundagent.core.dag.DagRuntime;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.dag.DagPlanNormalizer;
import com.fundagent.core.dag.FinalDagVerifier;
import com.fundagent.core.dag.NodeCompletionChecker;
import com.fundagent.core.dag.NodeExecutor;
import com.fundagent.core.dag.NodeRouter;
import com.fundagent.core.dag.ReplanPatchValidator;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.llm.LLMConfig;
import com.fundagent.core.llm.AgentLLMService;
import com.fundagent.core.llm.LLMCallIdGenerator;
import com.fundagent.core.llm.LLMContentHasher;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.llm.OpenAIService;
import com.fundagent.core.llm.Sha256LLMContentHasher;
import com.fundagent.core.llm.TraceableLLMService;
import com.fundagent.core.llm.UUIDLLMCallIdGenerator;
import com.fundagent.core.memory.DefaultMemoryAssembler;
import com.fundagent.core.memory.MemoryAssembler;
import com.fundagent.core.trace.TraceStore;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.catalog.ToolCatalog;
import com.fundagent.core.tool.catalog.ToolCatalogProvider;
import com.fundagent.core.tool.provider.ToolProvider;
import com.fundagent.core.tool.schema.ToolSchemaResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
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
    @Value("${agent.orchestration.context-rounds:10}")
    private int contextRounds;

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
    public OpenAIService openAIService() {
        return new OpenAIService(llmConfig());
    }

    @Bean("rawAgentLLMService")
    public AgentLLMService rawAgentLLMService(OpenAIService openAIService) {
        return openAIService;
    }

    @Bean
    public LLMContentHasher llmContentHasher() {
        return new Sha256LLMContentHasher();
    }

    @Bean
    public LLMCallIdGenerator llmCallIdGenerator() {
        return new UUIDLLMCallIdGenerator();
    }

    @Bean("traceableAgentLLMService")
    @ConditionalOnProperty(prefix = "agent.trace", name = "enabled", havingValue = "true")
    public AgentLLMService traceableAgentLLMService(
            @Qualifier("rawAgentLLMService") AgentLLMService delegate,
            TraceStore traceStore,
            LLMContentHasher contentHasher,
            LLMCallIdGenerator callIdGenerator,
            Clock traceClock) {
        return new TraceableLLMService(delegate, traceStore, contentHasher, callIdGenerator, traceClock);
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
    public DagPlanNormalizer dagPlanNormalizer() {
        return new DagPlanNormalizer();
    }

    @Bean
    public ReplanPatchValidator replanPatchValidator(CapabilityCatalog capabilityCatalog) {
        return new ReplanPatchValidator(capabilityCatalog);
    }

    @Bean
    public ToolBinder toolBinder(CapabilityCatalog capabilityCatalog, ToolSchemaResolver toolSchemaResolver) {
        return new DefaultToolBinder(capabilityCatalog, toolSchemaResolver);
    }

    @Bean
    public NodeRouter nodeRouter(List<NodeExecutor> executors) {
        log.info("NodeRouter initialized: executors={}", executors.size());
        return new DefaultNodeRouter(executors);
    }

    @Bean
    public NodeCompletionChecker nodeCompletionChecker() {
        return new DefaultNodeCompletionChecker();
    }

    @Bean
    public FinalDagVerifier finalDagVerifier() {
        return new DefaultFinalDagVerifier();
    }

    @Bean
    public DagRuntime dagRuntime(NodeRouter nodeRouter, NodeCompletionChecker nodeCompletionChecker,
                                 FinalDagVerifier finalDagVerifier) {
        return new DagRuntime(nodeRouter, nodeCompletionChecker, finalDagVerifier);
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
    public MemoryAssembler memoryAssembler() {
        return new DefaultMemoryAssembler(contextRounds);
    }

    @Bean
    public CapabilityDagPlanner capabilityDagPlanner(LLMService llmService,
                                                     CapabilityPlanningContextProvider planningContextProvider,
                                                     MemoryAssembler memoryAssembler,
                                                     DagPlanNormalizer dagPlanNormalizer) {
        return new CapabilityDagPlanner(llmService, planningContextProvider, memoryAssembler, dagPlanNormalizer);
    }

    @Bean
    public CapabilityDagRePlanner capabilityDagRePlanner(LLMService llmService,
                                                         CapabilityPlanningContextProvider planningContextProvider) {
        return new CapabilityDagRePlanner(llmService, planningContextProvider);
    }

    @Bean
    public ReplanningDagRuntime replanningDagRuntime(DagRuntime dagRuntime,
                                                     CapabilityDagRePlanner capabilityDagRePlanner,
                                                     ReplanPatchValidator replanPatchValidator,
                                                     ToolBinder toolBinder) {
        return new ReplanningDagRuntime(dagRuntime, capabilityDagRePlanner, replanPatchValidator, toolBinder);
    }

    @Bean
    public ToolNodeExecutor toolNodeExecutor(LLMService llmService, ToolRegistry toolRegistry) {
        return new ToolNodeExecutor(llmService, toolRegistry);
    }

    @Bean
    public ReasonNodeExecutor reasonNodeExecutor(LLMService llmService) {
        return new ReasonNodeExecutor(llmService);
    }

    @Bean
    public AnswerNodeExecutor answerNodeExecutor(LLMService llmService) {
        return new AnswerNodeExecutor(llmService);
    }

    @Bean
    public AskUserNodeExecutor askUserNodeExecutor() {
        return new AskUserNodeExecutor();
    }
}
