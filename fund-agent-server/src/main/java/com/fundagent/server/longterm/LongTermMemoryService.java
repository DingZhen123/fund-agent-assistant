package com.fundagent.server.longterm;

import java.util.List;
import java.util.Map;

public interface LongTermMemoryService {
    List<LongTermMemory> search(String userId, String query, int topK);

    LongTermMemoryRememberResult remember(String userId, List<Map<String, String>> messages,
                                          Map<String, Object> metadata);
}
