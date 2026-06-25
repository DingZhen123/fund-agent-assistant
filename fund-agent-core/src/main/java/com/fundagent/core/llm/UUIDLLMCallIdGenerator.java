package com.fundagent.core.llm;

import java.util.UUID;

public class UUIDLLMCallIdGenerator implements LLMCallIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
