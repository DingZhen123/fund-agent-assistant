package com.fundagent.mcpserver.tools;

import com.fundagent.mcpserver.tool.McpTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CommonMcpTools {

    @McpTool(
            name = "current_time",
            description = "获取指定时区的当前时间",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "timezone": {
                          "type": "string",
                          "description": "IANA时区，例如 Asia/Shanghai、UTC、America/New_York"
                        }
                      },
                      "required": ["timezone"],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> currentTime(Map<String, Object> args) {
        String timezone = stringArg(args, "timezone", "Asia/Shanghai");
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return Map.of(
                "timezone", timezone,
                "isoTime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "epochMillis", now.toInstant().toEpochMilli()
        );
    }

    @McpTool(
            name = "generate_uuid",
            description = "生成一个随机UUID",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {},
                      "required": [],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> generateUuid() {
        return Map.of("uuid", UUID.randomUUID().toString());
    }

    @McpTool(
            name = "text_stats",
            description = "统计文本长度、去空格长度和行数",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "text": {
                          "type": "string",
                          "description": "需要统计的文本"
                        }
                      },
                      "required": ["text"],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> textStats(Map<String, Object> args) {
        String text = stringArg(args, "text", "");
        return Map.of(
                "length", text.length(),
                "trimmedLength", text.trim().length(),
                "lineCount", text.isEmpty() ? 0 : text.split("\\R", -1).length,
                "blank", text.isBlank()
        );
    }

    @McpTool(
            name = "simple_calculator",
            description = "执行简单四则运算",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "left": {
                          "type": "number",
                          "description": "左操作数"
                        },
                        "right": {
                          "type": "number",
                          "description": "右操作数"
                        },
                        "operator": {
                          "type": "string",
                          "description": "运算符，支持 add、subtract、multiply、divide"
                        }
                      },
                      "required": ["left", "right", "operator"],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> simpleCalculator(Map<String, Object> args) {
        BigDecimal left = decimalArg(args, "left");
        BigDecimal right = decimalArg(args, "right");
        String operator = stringArg(args, "operator", "add");
        BigDecimal result = switch (operator) {
            case "add" -> left.add(right);
            case "subtract" -> left.subtract(right);
            case "multiply" -> left.multiply(right);
            case "divide" -> left.divide(right, 8, RoundingMode.HALF_UP).stripTrailingZeros();
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
        return Map.of(
                "left", left,
                "right", right,
                "operator", operator,
                "result", result
        );
    }

    @McpTool(
            name = "json_get_field",
            description = "从简单JSON对象中读取顶层字段",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "object": {
                          "type": "object",
                          "description": "需要读取字段的JSON对象"
                        },
                        "field": {
                          "type": "string",
                          "description": "顶层字段名"
                        }
                      },
                      "required": ["object", "field"],
                      "additionalProperties": false
                    }
                    """
    )
    @SuppressWarnings("unchecked")
    public Map<String, Object> jsonGetField(Map<String, Object> args) {
        Map<String, Object> object = args.get("object") instanceof Map
                ? (Map<String, Object>) args.get("object")
                : Map.of();
        String field = stringArg(args, "field", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("exists", object.containsKey(field));
        result.put("value", object.get(field));
        return result;
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args != null ? args.get(key) : null;
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private BigDecimal decimalArg(Map<String, Object> args, String key) {
        Object value = args != null ? args.get(key) : null;
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        throw new IllegalArgumentException("Missing number argument: " + key);
    }
}
