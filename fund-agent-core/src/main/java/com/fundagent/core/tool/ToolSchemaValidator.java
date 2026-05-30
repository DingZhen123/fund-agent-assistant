package com.fundagent.core.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.Map;

public class ToolSchemaValidator {

    public ToolParamValidationResult validate(ToolDefinition tool, Map<String, Object> args) {
        if (tool.getParamsSchemaJson() == null || tool.getParamsSchemaJson().isEmpty()) {
            return validateLegacyParams(tool, args);
        }

        JSONObject schema = JSON.parseObject(tool.getParamsSchemaJson());
        JSONObject properties = schema.getJSONObject("properties");
        JSONArray required = schema.getJSONArray("required");
        boolean additionalAllowed = schema.getBooleanValue("additionalProperties", true);

        if (args == null) {
            return ToolParamValidationResult.error("PARAMS_NULL", "工具参数不能为空");
        }

        if (required != null) {
            for (Object item : required) {
                String name = String.valueOf(item);
                Object value = args.get(name);
                if (value == null || value.toString().trim().isEmpty()) {
                    return ToolParamValidationResult.error("MISSING_REQUIRED_PARAM",
                            "缺少必需参数: " + name);
                }
            }
        }

        if (!additionalAllowed && properties != null) {
            for (String name : args.keySet()) {
                if (!properties.containsKey(name)) {
                    return ToolParamValidationResult.error("UNKNOWN_PARAM",
                            "工具" + tool.getName() + "不支持参数: " + name);
                }
            }
        }

        if (properties != null) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                JSONObject paramSchema = properties.getJSONObject(entry.getKey());
                if (paramSchema == null) continue;
                ToolParamValidationResult result = validateValue(entry.getKey(), entry.getValue(), paramSchema);
                if (!result.isValid()) return result;
            }
        }

        return ToolParamValidationResult.ok();
    }

    private ToolParamValidationResult validateLegacyParams(ToolDefinition tool, Map<String, Object> args) {
        if (args == null) {
            return ToolParamValidationResult.error("PARAMS_NULL", "工具参数不能为空");
        }
        for (String param : tool.getParams()) {
            Object value = args.get(param);
            if (value == null || value.toString().trim().isEmpty()) {
                return ToolParamValidationResult.error("MISSING_REQUIRED_PARAM",
                        "缺少必需参数: " + param);
            }
        }
        for (String param : args.keySet()) {
            if (!tool.getParams().contains(param)) {
                return ToolParamValidationResult.error("UNKNOWN_PARAM",
                        "工具" + tool.getName() + "不支持参数: " + param);
            }
        }
        return ToolParamValidationResult.ok();
    }

    private ToolParamValidationResult validateValue(String name, Object value, JSONObject schema) {
        if (value == null) return ToolParamValidationResult.ok();

        String type = schema.getString("type");
        if (type != null && !matchesType(value, type)) {
            return ToolParamValidationResult.error("INVALID_PARAM_TYPE",
                    "参数" + name + "类型错误，期望" + type);
        }

        JSONArray enums = schema.getJSONArray("enum");
        if (enums != null && !enums.contains(value)) {
            return ToolParamValidationResult.error("INVALID_PARAM_ENUM",
                    "参数" + name + "不在允许取值范围内");
        }

        String pattern = schema.getString("pattern");
        if (pattern != null && !pattern.isEmpty() && value instanceof String str
                && !str.matches(pattern)) {
            return ToolParamValidationResult.error("INVALID_PARAM_PATTERN",
                    "参数" + name + "格式不正确");
        }

        return ToolParamValidationResult.ok();
    }

    private boolean matchesType(Object value, String type) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number || value instanceof BigDecimal;
            case "object" -> value instanceof Map || value instanceof JSONObject;
            case "array" -> value instanceof Iterable || value instanceof JSONArray;
            default -> true;
        };
    }
}
