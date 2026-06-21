package com.fundagent.server.promptlab;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PromptLabRequest {
    private String systemPrompt;
    private String input;
    private String schemaName;
    private String schemaJson;
    private List<String> expectedContains = new ArrayList<>();
    private List<String> forbiddenContains = new ArrayList<>();
    private List<PromptTestCase> cases = new ArrayList<>();
}
