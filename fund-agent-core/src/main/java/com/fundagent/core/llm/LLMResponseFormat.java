package com.fundagent.core.llm;

import lombok.Value;

@Value
public class LLMResponseFormat {
    Type type;
    String schemaName;
    String schemaJson;

    public static LLMResponseFormat text() {
        return new LLMResponseFormat(Type.TEXT, null, null);
    }

    public static LLMResponseFormat jsonSchema(String schemaName, String schemaJson) {
        return new LLMResponseFormat(Type.JSON_SCHEMA, schemaName, schemaJson);
    }

    public enum Type {
        TEXT,
        JSON_SCHEMA
    }
}
