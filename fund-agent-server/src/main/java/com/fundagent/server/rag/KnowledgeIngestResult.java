package com.fundagent.server.rag;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeIngestResult {
    private String knowledgeBaseId;
    private String sourceDirectory;
    private int chunkCount;
    private List<String> sources;
}
