package com.fundagent.server.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeUploadResult {
    private String fileName;
    private String savedPath;
    private String knowledgeBaseId;
    private long size;
}
