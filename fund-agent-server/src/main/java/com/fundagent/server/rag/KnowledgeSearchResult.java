package com.fundagent.server.rag;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeSearchResult {
    private boolean matched;
    private double confidence;
    private String query;
    private String knowledgeBaseId;
    private List<KnowledgeSearchHit> hits;
    private List<String> sources;
}
