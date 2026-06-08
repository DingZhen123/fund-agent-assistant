package com.fundagent.agents.dag;

import com.alibaba.fastjson2.JSON;
import com.fundagent.common.model.Message;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanNormalizer;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.MemoryAssembler;
import com.fundagent.core.memory.MemoryContext;
import com.fundagent.core.memory.MemoryUseCase;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CapabilityDagPlanner {
    private final LLMService llmService;
    private final CapabilityPlanningContextProvider planningContextProvider;
    private final MemoryAssembler memoryAssembler;
    private final DagPlanNormalizer dagPlanNormalizer;

    public CapabilityDagPlanner(LLMService llmService, CapabilityPlanningContextProvider planningContextProvider,
                                MemoryAssembler memoryAssembler, DagPlanNormalizer dagPlanNormalizer) {
        this.llmService = llmService;
        this.planningContextProvider = planningContextProvider;
        this.memoryAssembler = memoryAssembler;
        this.dagPlanNormalizer = dagPlanNormalizer;
    }

    public DagPlan plan(Memory memory, String userMessage) {
        CapabilityPlanningContext planningContext = planningContextProvider.build(memory, userMessage);

        List<Message> history = new ArrayList<>();
        MemoryContext memoryContext = memoryAssembler.assemble(memory, userMessage, MemoryUseCase.GRAPH_PLANNER);
        String context = memoryContext.toPromptText();
        if (context != null && !context.isEmpty()) {
            history.add(new Message("user", "[当前会话上下文]\n" + context));
        }

        String raw = llmService.chatStructured(
                buildSystemPrompt(planningContext),
                history,
                userMessage,
                "capability_dag",
                planningContext.getDagPlanSchema());
        log.info("CapabilityDagPlanner raw: {}", raw);
        return dagPlanNormalizer.normalize(JSON.parseObject(raw, DagPlan.class));
    }

    private String buildSystemPrompt(CapabilityPlanningContext planningContext) {
        return """
                你是企业级DAG Agent Runtime中的CapabilityDagPlanner。
                你的职责是把用户目标拆解为抽象能力节点DAG，而不是直接选择具体工具。

                可用能力:
                %s

                规则:
                1. 只输出符合JSON Schema的DagPlan。
                2. capability必须逐字使用可用能力中的名称，不要编造能力。
                3. node_type必须与所选capability的nodeType一致。
                4. 第一版按拓扑串行执行设计，但仍需用depends_on表达节点依赖。
                5. 工具调用不是你的职责；需要业务能力时选择对应capability即可。
                6. 普通解释、总结、判断类任务使用reason.general或conversation.answer。
                7. 缺少必要信息时使用user.ask_clarification。
                8. 最后必须包含conversation.answer能力节点作为最终回复节点。
                """.formatted(planningContext.getCapabilitiesDescription());
    }
}
