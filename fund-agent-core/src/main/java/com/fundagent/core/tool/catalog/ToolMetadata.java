package com.fundagent.core.tool.catalog;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ToolMetadata {
    private String name;
    private String description;
    private String domain;
    private String version;
    private String riskLevel;
    private List<String> intents;
    private String providerType;
    private boolean requiresAuth;
    private boolean requiresConfirmation;
    private boolean enabled;
    private List<String> params;
    private Map<String, Object> metadata;
}
