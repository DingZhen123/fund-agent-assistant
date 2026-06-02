package com.fundagent.server.tool.selection;

import com.fundagent.core.tool.catalog.ToolMetadata;
import com.fundagent.core.tool.selection.ToolSelectionContext;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Order(400)
public class ToolRankingStage implements ToolSelectionStage {

    @Override
    public void apply(ToolSelectionContext context) {
        String message = context.getUserMessage();
        List<ToolMetadata> ranked = context.getCandidateTools().stream()
                .sorted(Comparator
                        .comparingInt((ToolMetadata tool) -> score(tool, message)).reversed()
                        .thenComparing(ToolMetadata::getName))
                .toList();
        context.replaceCandidateTools(ranked);
        context.addMatchedRule("ranking:keyword_score");
    }

    private int score(ToolMetadata tool, String message) {
        int score = 0;
        if (contains(message, tool.getName())) {
            score += 20;
        }
        if (contains(message, tool.getDescription())) {
            score += 20;
        }
        if (tool.getParams() != null) {
            for (String param : tool.getParams()) {
                if (contains(message, param)) {
                    score += 5;
                }
            }
        }
        return score;
    }

    private boolean contains(String message, String value) {
        return message != null && value != null && !value.isBlank() && message.contains(value);
    }
}
