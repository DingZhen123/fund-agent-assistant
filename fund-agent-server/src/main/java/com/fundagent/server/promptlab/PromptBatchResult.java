package com.fundagent.server.promptlab;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PromptBatchResult {
    private int total;
    private int passed;
    private double passRate;
    private long elapsedMs;
    private List<PromptTestResult> results;
}
