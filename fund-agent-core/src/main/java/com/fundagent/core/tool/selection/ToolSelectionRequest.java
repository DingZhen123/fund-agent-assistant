package com.fundagent.core.tool.selection;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolSelectionRequest {
    private String userMessage;
    private String userId;
    private String conversationId;
    @Builder.Default
    private int maxCandidates = 10;
}
