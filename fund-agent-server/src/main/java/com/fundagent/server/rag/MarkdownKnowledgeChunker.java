package com.fundagent.server.rag;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class MarkdownKnowledgeChunker {
    private static final int TARGET_CHARS = 700;
    private static final int MAX_CHARS = 900;
    private static final int MIN_CHARS = 180;
    private static final int OVERLAP_CHARS = 120;

    public List<KnowledgeChunk> chunkDirectory(Path directory, String knowledgeBaseId) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths
                    .filter(item -> item.toString().endsWith(".md"))
                    .sorted()
                    .toList()) {
                chunks.addAll(chunkFile(path, knowledgeBaseId));
            }
        }
        return chunks;
    }

    public List<KnowledgeChunk> chunkFile(Path path, String knowledgeBaseId) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String title = extractTitle(content, path);
        String docId = stableId(path.toString());
        List<Section> sections = splitSections(content, title);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Section section : sections) {
            for (String chunkContent : splitSectionContent(section.content())) {
                chunks.add(KnowledgeChunk.builder()
                        .chunkId(docId + ":" + chunkIndex)
                        .docId(docId)
                        .knowledgeBaseId(knowledgeBaseId)
                        .title(title)
                        .sectionPath(title + " > " + section.heading())
                        .content(chunkContent)
                        .source(path.toString())
                        .chunkIndex(chunkIndex)
                        .build());
                chunkIndex++;
            }
        }
        return chunks;
    }

    private List<Section> splitSections(String content, String documentTitle) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = documentTitle;
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) {
                addSection(sections, currentHeading, current);
                currentHeading = line.substring(3).trim();
                current = new StringBuilder();
            } else {
                current.append(line).append("\n");
            }
        }
        addSection(sections, currentHeading, current);
        return sections;
    }

    private void addSection(List<Section> sections, String heading, StringBuilder content) {
        String text = content.toString().trim();
        if (!text.isBlank()) {
            sections.add(new Section(heading, text));
        }
    }

    private List<String> splitSectionContent(String content) {
        List<String> paragraphs = splitParagraphs(content);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > MAX_CHARS) {
                flushChunk(chunks, current);
                chunks.addAll(splitLongParagraph(paragraph));
                continue;
            }
            if (current.length() + paragraph.length() + 2 > TARGET_CHARS && current.length() >= MIN_CHARS) {
                flushChunk(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.trim();
            if (!normalized.isBlank()) {
                paragraphs.add(normalized);
            }
        }
        return paragraphs;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[。！？；;])")) {
            String normalized = sentence.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (current.length() + normalized.length() > TARGET_CHARS && current.length() >= MIN_CHARS) {
                flushChunk(chunks, current);
            }
            current.append(normalized);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        String text = current.toString().trim();
        if (!text.isBlank()) {
            chunks.add(text);
            String overlap = tail(text, OVERLAP_CHARS);
            current.setLength(0);
            if (!overlap.isBlank()) {
                current.append(overlap);
            }
        }
    }

    private String tail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int start = Math.max(0, text.length() - maxChars);
        int sentenceStart = Math.max(
                Math.max(text.lastIndexOf('。', start), text.lastIndexOf('；', start)),
                Math.max(text.lastIndexOf('！', start), text.lastIndexOf('？', start)));
        if (sentenceStart >= 0 && sentenceStart + 1 < text.length()) {
            return text.substring(sentenceStart + 1).trim();
        }
        return text.substring(start).trim();
    }

    private String extractTitle(String content, Path path) {
        return content.lines()
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .findFirst()
                .orElse(path.getFileName().toString());
    }

    private String stableId(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Section(String heading, String content) {
    }
}
