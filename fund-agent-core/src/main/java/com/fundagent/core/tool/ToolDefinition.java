package com.fundagent.core.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;
    private String domain;
    private String version;
    private String riskLevel;
    @Builder.Default
    private boolean enabled = true;
    private List<String> params;
    private String paramsSchemaJson;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private Method method;

    public Object invoke(Object instance, Map<String, Object> args) throws Exception {
        if (method.getParameterCount() == 1) {
            return method.invoke(instance, args);
        }
        if (method.getParameterCount() == 0) {
            return method.invoke(instance);
        }
        return method.invoke(instance, args);
    }
}
