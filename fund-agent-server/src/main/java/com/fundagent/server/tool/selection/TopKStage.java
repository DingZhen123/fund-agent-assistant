package com.fundagent.server.tool.selection;

import com.fundagent.core.tool.catalog.ToolMetadata;
import com.fundagent.core.tool.selection.ToolSelectionContext;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(500)
public class TopKStage implements ToolSelectionStage {

    @Override
    public void apply(ToolSelectionContext context) {
        List<ToolMetadata> tools = context.getCandidateTools();
        int maxCandidates = context.getMaxCandidates();
        if (tools.size() > maxCandidates) {
            context.replaceCandidateTools(tools.subList(0, maxCandidates));
            context.addMatchedRule("topk:limit=" + maxCandidates);
        } else {
            context.addMatchedRule("topk:no_limit");
        }
    }
}
