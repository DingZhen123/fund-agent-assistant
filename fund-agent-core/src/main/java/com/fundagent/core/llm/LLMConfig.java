package com.fundagent.core.llm;

import lombok.Data;

@Data
public class LLMConfig {
    private String apiBase;
    private String apiKey;
    private String model = "gpt-4";
    private double temperature = 0.1;
    private int maxTokens = 2048;
    private int timeoutSeconds = 60;
}
