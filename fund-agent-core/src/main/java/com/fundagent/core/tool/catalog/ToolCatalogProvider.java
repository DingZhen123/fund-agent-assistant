package com.fundagent.core.tool.catalog;

import java.util.List;

public interface ToolCatalogProvider {
    String providerType();

    List<ToolMetadata> loadTools();
}
