package com.fundagent.core.memory;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MemoryContext {
    private MemoryUseCase useCase;
    private String summary;
    private String shortTermContext;
    private Map<String, Object> entities = new LinkedHashMap<>();
    private List<String> longTermMemories = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();

        if (summary != null && !summary.isEmpty()) {
            sb.append("[历史摘要]\n").append(summary).append("\n\n");
        }

        if (!longTermMemories.isEmpty()) {
            sb.append("[长期记忆]\n");
            for (String memory : longTermMemories) {
                sb.append("- ").append(memory).append("\n");
            }
            sb.append("\n");
        }

        if (!entities.isEmpty()) {
            sb.append("[关键实体]\n");
            entities.forEach((key, value) -> sb.append(key).append(": ").append(value).append("\n"));
            sb.append("\n");
        }

        if (shortTermContext != null && !shortTermContext.isEmpty()) {
            sb.append("[最近对话]\n").append(shortTermContext).append("\n");
        }

        return sb.toString().trim();
    }
}
