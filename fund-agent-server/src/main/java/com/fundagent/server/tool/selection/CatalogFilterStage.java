package com.fundagent.server.tool.selection;

import com.fundagent.core.tool.catalog.ToolCatalog;
import com.fundagent.core.tool.catalog.ToolMetadata;
import com.fundagent.core.tool.selection.ToolSelectionContext;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(300)
public class CatalogFilterStage implements ToolSelectionStage {
    private final ToolCatalog toolCatalog;

    public CatalogFilterStage(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    @Override
    public void apply(ToolSelectionContext context) {
        List<ToolMetadata> tools = filterByDomain(context);
        if (!context.getIntents().isEmpty()) {
            tools = filterByIntent(tools, context.getIntents());
        }
        context.replaceCandidateTools(tools);
        context.addMatchedRule("catalog_filter:candidates=" + tools.size());
    }

    private List<ToolMetadata> filterByDomain(ToolSelectionContext context) {
        if (context.getDomain() == null || context.getDomain().isBlank()) {
            return List.of();
        }
        return toolCatalog.findByDomain(context.getDomain());
    }

    private List<ToolMetadata> filterByIntent(List<ToolMetadata> tools, Set<String> intents) {
        List<ToolMetadata> matched = tools.stream()
                .filter(tool -> tool.getIntents() != null
                        && tool.getIntents().stream().anyMatch(intents::contains))
                .collect(Collectors.toList());
        if (!matched.isEmpty()) {
            return matched;
        }
        return tools;
    }
}
