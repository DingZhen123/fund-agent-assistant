package com.fundagent.core.memory;

import java.util.ArrayList;
import java.util.List;

public class RecentRoundsMemorySelector implements ShortTermMemorySelector {
    @Override
    public List<Round> select(Memory memory, int maxRounds, boolean includeCurrentIncompleteRound) {
        if (memory == null || memory.getRounds() == null || memory.getRounds().isEmpty() || maxRounds <= 0) {
            return List.of();
        }

        List<Round> rounds = new ArrayList<>(memory.getRounds());
        if (!includeCurrentIncompleteRound && !rounds.isEmpty()) {
            Round lastRound = rounds.get(rounds.size() - 1);
            if (!lastRound.isCompleted()) {
                rounds.remove(rounds.size() - 1);
            }
        }

        int start = Math.max(0, rounds.size() - maxRounds);
        return new ArrayList<>(rounds.subList(start, rounds.size()));
    }
}
