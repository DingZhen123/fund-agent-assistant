package com.fundagent.core.tool.selection;

import com.fundagent.core.tool.catalog.ToolMetadata;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ToolSelectionResult {
    private String domain;
    private List<String> intents;
    private List<ToolMetadata> candidateTools;
    private List<String> candidateToolNames;
    private double confidence;
    private String reason;
    private List<String> matchedRules;
}
