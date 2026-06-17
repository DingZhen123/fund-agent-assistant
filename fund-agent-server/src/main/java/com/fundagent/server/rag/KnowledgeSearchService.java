package com.fundagent.server.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeSearchService {
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private final EmbeddingService embeddingService;
    private final QdrantVectorStore vectorStore;
    private final String defaultKnowledgeBaseId;

    public KnowledgeSearchService(EmbeddingService embeddingService,
                                  QdrantVectorStore vectorStore,
                                  @Value("${agent.rag.default-knowledge-base-id:fund.payment.sop}") String defaultKnowledgeBaseId) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
    }

    public KnowledgeSearchResult search(String query, String knowledgeBaseId, Integer topK) {
        String safeKnowledgeBaseId = knowledgeBaseId == null || knowledgeBaseId.isBlank()
                ? defaultKnowledgeBaseId
                : knowledgeBaseId;
        int safeTopK = topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(MAX_TOP_K, topK));
        List<Double> queryVector = embeddingService.embed(query);
        List<KnowledgeSearchHit> hits = vectorStore.search(queryVector, safeKnowledgeBaseId, safeTopK);
        boolean matched = !hits.isEmpty();
        double confidence = matched ? hits.get(0).getScore() : 0.0D;
        return KnowledgeSearchResult.builder()
                .matched(matched)
                .confidence(confidence)
                .query(query)
                .knowledgeBaseId(safeKnowledgeBaseId)
                .hits(hits)
                .sources(hits.stream().map(KnowledgeSearchHit::getSource).distinct().toList())
                .build();
    }
}
