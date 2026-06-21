package com.fundagent.server.promptlab;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PromptTestResult {
    private String name;
    private String input;
    private String output;
    private long elapsedMs;
    private boolean passed;
    private List<PromptAssertionResult> assertions;
}
