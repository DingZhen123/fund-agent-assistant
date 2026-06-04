package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fundagent.core.tool.ToolDefinition;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BoundTool {
    @JSONField(name = "tool_name")
    private String toolName;

    private String description;

    private String domain;

    private String version;

    @JSONField(name = "risk_level")
    private String riskLevel;

    private List<String> intents;

    @JSONField(name = "provider_type")
    private String providerType;

    @JSONField(name = "requires_auth")
    private boolean requiresAuth;

    @JSONField(name = "requires_confirmation")
    private boolean requiresConfirmation;

    private List<String> params;

    @JSONField(name = "params_schema_json")
    private String paramsSchemaJson;

    private Map<String, Object> metadata;

    public static BoundTool fromDefinition(ToolDefinition definition) {
        return BoundTool.builder()
                .toolName(definition.getName())
                .description(definition.getDescription())
                .domain(definition.getDomain())
                .version(definition.getVersion())
                .riskLevel(definition.getRiskLevel())
                .intents(definition.getIntents())
                .providerType(definition.getProviderType())
                .requiresAuth(definition.isRequiresAuth())
                .requiresConfirmation(definition.isRequiresConfirmation())
                .params(definition.getParams())
                .paramsSchemaJson(definition.getParamsSchemaJson())
                .metadata(definition.getMetadata())
                .build();
    }
}
