package com.fundagent.core.tool.selection;

import com.fundagent.core.tool.catalog.ToolMetadata;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class ToolSelectionContext {
    private final ToolSelectionRequest request;
    private String domain;
    private double confidence;
    private String reason;
    private final Set<String> intents = new LinkedHashSet<>();
    private final List<ToolMetadata> candidateTools = new ArrayList<>();
    private final List<String> matchedRules = new ArrayList<>();

    private ToolSelectionContext(ToolSelectionRequest request) {
        this.request = request;
    }

    public static ToolSelectionContext from(ToolSelectionRequest request) {
        return new ToolSelectionContext(request);
    }

    public String getUserMessage() {
        return request != null && request.getUserMessage() != null
                ? request.getUserMessage().trim()
                : "";
    }

    public int getMaxCandidates() {
        if (request == null || request.getMaxCandidates() <= 0) {
            return 10;
        }
        return request.getMaxCandidates();
    }

    public void setDomain(String domain, double confidence, String reason) {
        this.domain = domain;
        this.confidence = Math.max(this.confidence, confidence);
        this.reason = reason;
    }

    public void addIntent(String intent) {
        if (intent != null && !intent.trim().isEmpty()) {
            intents.add(intent.trim());
        }
    }

    public void addMatchedRule(String rule) {
        if (rule != null && !rule.trim().isEmpty()) {
            matchedRules.add(rule);
        }
    }

    public void replaceCandidateTools(List<ToolMetadata> tools) {
        candidateTools.clear();
        if (tools != null) {
            candidateTools.addAll(tools);
        }
    }

    public ToolSelectionResult toResult() {
        List<ToolMetadata> tools = List.copyOf(candidateTools);
        return ToolSelectionResult.builder()
                .domain(domain)
                .intents(List.copyOf(intents))
                .candidateTools(tools)
                .candidateToolNames(tools.stream()
                        .map(ToolMetadata::getName)
                        .collect(Collectors.toList()))
                .confidence(confidence)
                .reason(reason)
                .matchedRules(List.copyOf(matchedRules))
                .build();
    }
}
