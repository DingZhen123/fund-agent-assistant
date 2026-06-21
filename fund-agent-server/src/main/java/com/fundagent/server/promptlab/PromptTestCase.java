package com.fundagent.server.promptlab;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PromptTestCase {
    private String name;
    private String input;
    private List<String> expectedContains = new ArrayList<>();
    private List<String> forbiddenContains = new ArrayList<>();
}
