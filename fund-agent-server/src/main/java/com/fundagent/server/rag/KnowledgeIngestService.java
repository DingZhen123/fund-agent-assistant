package com.fundagent.server.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KnowledgeIngestService {
    private final MarkdownKnowledgeChunker chunker;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStore vectorStore;
    private final String defaultKnowledgeBaseId;
    private final String defaultSourceDirectory;

    public KnowledgeIngestService(MarkdownKnowledgeChunker chunker,
                                  EmbeddingService embeddingService,
                                  QdrantVectorStore vectorStore,
                                  @Value("${agent.rag.default-knowledge-base-id:fund.payment.sop}") String defaultKnowledgeBaseId,
                                  @Value("${agent.rag.source-directory:docs/knowledge}") String defaultSourceDirectory) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
        this.defaultSourceDirectory = defaultSourceDirectory;
    }

    public KnowledgeIngestResult ingestDefaultDirectory() {
        return ingestDirectory(defaultSourceDirectory, defaultKnowledgeBaseId);
    }

    public KnowledgeIngestResult ingestDirectory(String sourceDirectory, String knowledgeBaseId) {
        String safeDirectory = isBlank(sourceDirectory) ? defaultSourceDirectory : sourceDirectory;
        String safeKnowledgeBaseId = isBlank(knowledgeBaseId) ? defaultKnowledgeBaseId : knowledgeBaseId;
        try {
            List<KnowledgeChunk> chunks = chunker.chunkDirectory(Path.of(safeDirectory), safeKnowledgeBaseId);
            List<List<Double>> vectors = new ArrayList<>(chunks.size());
            for (KnowledgeChunk chunk : chunks) {
                vectors.add(embeddingService.embed(toEmbeddingText(chunk)));
            }
            vectorStore.upsert(chunks, vectors);
            log.info("Knowledge ingested: knowledgeBaseId={}, sourceDirectory={}, chunks={}",
                    safeKnowledgeBaseId, safeDirectory, chunks.size());
            return KnowledgeIngestResult.builder()
                    .knowledgeBaseId(safeKnowledgeBaseId)
                    .sourceDirectory(safeDirectory)
                    .chunkCount(chunks.size())
                    .sources(chunks.stream().map(KnowledgeChunk::getSource).distinct().toList())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("读取知识库文档失败: " + e.getMessage(), e);
        }
    }

    private String toEmbeddingText(KnowledgeChunk chunk) {
        return """
                标题：%s
                章节：%s
                正文：%s
                """.formatted(chunk.getTitle(), chunk.getSectionPath(), chunk.getContent());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
