package com.fundagent.core.agent;

import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.post.Post;
import com.fundagent.core.tool.ToolRegistry;

import java.util.function.Consumer;

public abstract class Agent {
    protected String agentName;
    protected String agentDescription;
    protected LLMService llmService;
    protected ToolRegistry toolRegistry;

    public Agent(AgentEntry entry) {
        this.agentName = entry.getName();
        this.agentDescription = entry.getDescription();
    }

    public abstract Post reply(Memory memory, Post incoming);

    public Post reply(Memory memory, Post incoming, Consumer<String> onToken) {
        return reply(memory, incoming);
    }

    public void setLlmService(LLMService llmService) {
        this.llmService = llmService;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentDescription() {
        return agentDescription;
    }

    public void init() {}
    public void close() {}
}
