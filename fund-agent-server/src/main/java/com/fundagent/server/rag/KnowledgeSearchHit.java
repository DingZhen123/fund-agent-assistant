package com.fundagent.server.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSearchHit {
    private String chunkId;
    private String docId;
    private String title;
    private String sectionPath;
    private String content;
    private String source;
    private double score;
}
