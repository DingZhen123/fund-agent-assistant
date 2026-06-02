package com.fundagent.core.tool.selection;

import java.util.List;

public class DefaultToolSelector implements ToolSelector {
    private final List<ToolSelectionStage> stages;

    public DefaultToolSelector(List<ToolSelectionStage> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public ToolSelectionResult select(ToolSelectionRequest request) {
        ToolSelectionContext context = ToolSelectionContext.from(request);
        for (ToolSelectionStage stage : stages) {
            stage.apply(context);
        }
        return context.toResult();
    }
}
