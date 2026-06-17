package com.fundagent.server.tool;

import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.ToolResult;
import com.fundagent.server.rag.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class KnowledgeSearchTool {
    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchTool(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Tool(name = "knowledgeSearch",
          description = "查询企业知识库、SOP、FAQ、制度和业务规则",
          params = {"query", "knowledgeBaseId", "topK"})
    public ToolResult search(Map<String, Object> args) {
        String query = asString(args.get("query"));
        if (query == null || query.isBlank()) {
            return ToolResult.validationError("QUERY_REQUIRED", "query不能为空");
        }
        try {
            return ToolResult.success(knowledgeSearchService.search(
                    query,
                    asString(args.get("knowledgeBaseId")),
                    asInteger(args.get("topK"))));
        } catch (Exception e) {
            log.warn("Knowledge search failed", e);
            return ToolResult.systemError("KNOWLEDGE_SEARCH_FAILED",
                    "知识库检索失败: " + e.getMessage(), true);
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
