package com.fundagent.core.llm;

@FunctionalInterface
public interface LLMCallIdGenerator {

    String nextId();
}
