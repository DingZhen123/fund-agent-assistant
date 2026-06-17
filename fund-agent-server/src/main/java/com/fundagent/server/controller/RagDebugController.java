package com.fundagent.server.controller;

import com.alibaba.fastjson2.JSONObject;
import com.fundagent.server.rag.KnowledgeIngestResult;
import com.fundagent.server.rag.KnowledgeIngestService;
import com.fundagent.server.rag.KnowledgeUploadAndIngestResult;
import com.fundagent.server.rag.KnowledgeDocumentStorageService;
import com.fundagent.server.rag.KnowledgeSearchResult;
import com.fundagent.server.rag.KnowledgeSearchService;
import com.fundagent.server.rag.KnowledgeUploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/debug/rag")
@CrossOrigin("*")
public class RagDebugController {
    private final KnowledgeIngestService ingestService;
    private final KnowledgeSearchService searchService;
    private final KnowledgeDocumentStorageService storageService;

    public RagDebugController(KnowledgeIngestService ingestService,
                              KnowledgeSearchService searchService,
                              KnowledgeDocumentStorageService storageService) {
        this.ingestService = ingestService;
        this.searchService = searchService;
        this.storageService = storageService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeUploadResult upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
        log.info("debug rag upload: file={}, knowledgeBaseId={}",
                file != null ? file.getOriginalFilename() : null, knowledgeBaseId);
        return storageService.upload(file, knowledgeBaseId);
    }

    @PostMapping(path = "/upload-and-ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeUploadAndIngestResult uploadAndIngest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
        KnowledgeUploadResult upload = storageService.upload(file, knowledgeBaseId);
        KnowledgeIngestResult ingest = ingestService.ingestDirectory(null, upload.getKnowledgeBaseId());
        return KnowledgeUploadAndIngestResult.builder()
                .upload(upload)
                .ingest(ingest)
                .build();
    }

    @PostMapping("/ingest")
    public KnowledgeIngestResult ingest(@RequestBody(required = false) JSONObject request) {
        String sourceDirectory = request != null ? request.getString("sourceDirectory") : null;
        String knowledgeBaseId = request != null ? request.getString("knowledgeBaseId") : null;
        log.info("debug rag ingest: sourceDirectory={}, knowledgeBaseId={}", sourceDirectory, knowledgeBaseId);
        return ingestService.ingestDirectory(sourceDirectory, knowledgeBaseId);
    }

    @PostMapping("/search")
    public KnowledgeSearchResult search(@RequestBody JSONObject request) {
        String query = request.getString("query");
        String knowledgeBaseId = request.getString("knowledgeBaseId");
        Integer topK = request.getInteger("topK");
        log.info("debug rag search: query={}, knowledgeBaseId={}, topK={}", query, knowledgeBaseId, topK);
        return searchService.search(query, knowledgeBaseId, topK);
    }
}
