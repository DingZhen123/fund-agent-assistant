package com.fundagent.server.promptlab;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PromptAssertionResult {
    private String type;
    private String value;
    private boolean passed;
}
