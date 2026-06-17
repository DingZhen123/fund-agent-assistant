package com.fundagent.server.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
public class KnowledgeDocumentStorageService {
    private final String defaultKnowledgeBaseId;
    private final String defaultSourceDirectory;

    public KnowledgeDocumentStorageService(
            @Value("${agent.rag.default-knowledge-base-id:fund.payment.sop}") String defaultKnowledgeBaseId,
            @Value("${agent.rag.source-directory:docs/knowledge}") String defaultSourceDirectory) {
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
        this.defaultSourceDirectory = defaultSourceDirectory;
    }

    public KnowledgeUploadResult upload(MultipartFile file, String knowledgeBaseId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String safeFileName = sanitizeFileName(originalName);
        if (!safeFileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("当前仅支持上传Markdown文档(.md)");
        }

        try {
            Path directory = Path.of(defaultSourceDirectory);
            Files.createDirectories(directory);
            Path target = directory.resolve(safeFileName).normalize();
            if (!target.startsWith(directory.normalize())) {
                throw new IllegalArgumentException("非法文件名");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return KnowledgeUploadResult.builder()
                    .fileName(safeFileName)
                    .savedPath(target.toString())
                    .knowledgeBaseId(isBlank(knowledgeBaseId) ? defaultKnowledgeBaseId : knowledgeBaseId)
                    .size(file.getSize())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("保存知识库文档失败: " + e.getMessage(), e);
        }
    }

    private String sanitizeFileName(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "knowledge.md" : originalName;
        name = name.replace('\\', '/');
        int slashIndex = name.lastIndexOf('/');
        if (slashIndex >= 0) {
            name = name.substring(slashIndex + 1);
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
