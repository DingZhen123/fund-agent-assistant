package com.fundagent.core.capability;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CapabilityDefinition {
    private String name;
    private String nodeType;
    private String domain;
    private List<String> intents;
    private String description;
    private List<String> bindableTools;
    private boolean enabled;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
