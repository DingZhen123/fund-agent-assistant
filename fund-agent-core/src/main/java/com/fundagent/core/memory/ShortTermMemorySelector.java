package com.fundagent.core.memory;

import java.util.List;

public interface ShortTermMemorySelector {
    List<Round> select(Memory memory, int maxRounds, boolean includeCurrentIncompleteRound);
}
