package com.fundagent.server.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeUploadAndIngestResult {
    private KnowledgeUploadResult upload;
    private KnowledgeIngestResult ingest;
}
