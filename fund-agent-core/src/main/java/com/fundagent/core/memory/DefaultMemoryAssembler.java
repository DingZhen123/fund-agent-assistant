package com.fundagent.core.memory;

import java.util.List;
import java.util.Map;

public class DefaultMemoryAssembler implements MemoryAssembler {
    private final int defaultRecentRounds;
    private final ShortTermMemorySelector shortTermMemorySelector;

    public DefaultMemoryAssembler(int defaultRecentRounds) {
        this(defaultRecentRounds, new RecentRoundsMemorySelector());
    }

    public DefaultMemoryAssembler(int defaultRecentRounds, ShortTermMemorySelector shortTermMemorySelector) {
        this.defaultRecentRounds = defaultRecentRounds;
        this.shortTermMemorySelector = shortTermMemorySelector;
    }

    @Override
    public MemoryContext assemble(Memory memory, String userMessage, MemoryUseCase useCase) {
        MemoryContext context = new MemoryContext();
        context.setUseCase(useCase);

        if (memory == null) {
            return context;
        }

        context.setSummary(memory.getCompressedSummary());
        if (memory.getSharedMemory() != null) {
            context.setEntities(memory.getSharedMemory().snapshot());
        }
        context.setShortTermContext(buildShortTermContext(memory, useCase));
        context.getMetadata().put("conversationId", memory.getConversationId());
        context.getMetadata().put("totalRounds", memory.getTotalRounds());
        return context;
    }

    private String buildShortTermContext(Memory memory, MemoryUseCase useCase) {
        int maxRounds = resolveRecentRounds(useCase);
        boolean includeCurrentIncompleteRound = includeCurrentIncompleteRound(useCase);
        List<Round> rounds = shortTermMemorySelector.select(memory, maxRounds, includeCurrentIncompleteRound);
        if (rounds.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Round round : rounds) {
            sb.append(round.toPromptString()).append("\n");
        }
        return sb.toString().trim();
    }

    private int resolveRecentRounds(MemoryUseCase useCase) {
        return switch (useCase) {
            case SIMPLE_PLANNER -> defaultRecentRounds;
            case GRAPH_PLANNER -> Math.min(defaultRecentRounds, 5);
            case GRAPH_ANSWER -> Math.min(defaultRecentRounds, 2);
            case ENTITY_EXTRACTION -> Math.min(defaultRecentRounds, 3);
            case SUMMARY -> defaultRecentRounds;
        };
    }

    private boolean includeCurrentIncompleteRound(MemoryUseCase useCase) {
        return false;
    }
}
