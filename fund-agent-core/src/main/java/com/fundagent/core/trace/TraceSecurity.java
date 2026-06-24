package com.fundagent.core.trace;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TraceSecurity {
    public static final String GENESIS_HASH = "0".repeat(64);

    private final TraceCanonicalizer canonicalizer;
    private final TraceHasher hasher;
    private final TraceSigner signer;

    public TraceSecurity(TraceCanonicalizer canonicalizer, TraceHasher hasher, TraceSigner signer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    public TraceEvent protectEvent(TraceEvent draft) {
        Objects.requireNonNull(draft, "draft");
        String canonicalPayload = canonicalizer.canonicalizeJson(draft.getPayloadJson());
        String payloadHash = hasher.hash(canonicalPayload);
        TraceEvent normalized = draft.toBuilder()
                .payloadJson(canonicalPayload)
                .payloadHash(payloadHash)
                .build();
        String eventHash = hasher.hash(canonicalEvent(normalized));
        TraceSignature signature = signer.sign(eventSignatureMaterial(
                normalized.getEpisodeCode(),
                normalized.getSequenceNo(),
                eventHash));
        return normalized.toBuilder()
                .eventHash(eventHash)
                .signatureAlgorithm(signature.getAlgorithm())
                .signingKeyId(signature.getKeyId())
                .eventSignature(signature.getValue())
                .build();
    }

    public boolean verifyEvent(TraceEvent event) {
        if (event == null) {
            return false;
        }
        try {
            String canonicalPayload = canonicalizer.canonicalizeJson(event.getPayloadJson());
            if (!hasher.hash(canonicalPayload).equals(event.getPayloadHash())) {
                return false;
            }
            TraceEvent normalized = event.toBuilder().payloadJson(canonicalPayload).build();
            String expectedHash = hasher.hash(canonicalEvent(normalized));
            if (!expectedHash.equals(event.getEventHash())) {
                return false;
            }
            return signer.verify(
                    eventSignatureMaterial(event.getEpisodeCode(), event.getSequenceNo(), event.getEventHash()),
                    new TraceSignature(
                            event.getSignatureAlgorithm(),
                            event.getSigningKeyId(),
                            event.getEventSignature()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public EpisodeSeal createSeal(AgentEpisode episode, String sealCode, String actor, Instant sealedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("episodeCode", episode.getEpisodeCode());
        fields.put("eventCount", episode.getEventCount());
        fields.put("finalEventHash", episode.getLastEventHash());
        fields.put("finalSequenceNo", episode.getNextSequenceNo() - 1);
        fields.put("finalStatus", episode.getStatus().name());
        fields.put("sealCode", sealCode);
        fields.put("sealedAt", sealedAt.toString());
        String material = canonicalizer.canonicalizeFields(fields);
        TraceSignature signature = signer.sign(material);
        return EpisodeSeal.builder()
                .sealCode(sealCode)
                .episodeCode(episode.getEpisodeCode())
                .finalSequenceNo(episode.getNextSequenceNo() - 1)
                .finalEventHash(episode.getLastEventHash())
                .eventCount(episode.getEventCount())
                .finalStatus(episode.getStatus())
                .sealedAt(sealedAt)
                .signatureAlgorithm(signature.getAlgorithm())
                .signingKeyId(signature.getKeyId())
                .sealSignature(signature.getValue())
                .createdAt(sealedAt)
                .createdBy(actor)
                .build();
    }

    public boolean verifySeal(EpisodeSeal seal) {
        if (seal == null) {
            return false;
        }
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("episodeCode", seal.getEpisodeCode());
            fields.put("eventCount", seal.getEventCount());
            fields.put("finalEventHash", seal.getFinalEventHash());
            fields.put("finalSequenceNo", seal.getFinalSequenceNo());
            fields.put("finalStatus", seal.getFinalStatus().name());
            fields.put("sealCode", seal.getSealCode());
            fields.put("sealedAt", seal.getSealedAt().toString());
            return signer.verify(
                    canonicalizer.canonicalizeFields(fields),
                    new TraceSignature(
                            seal.getSignatureAlgorithm(),
                            seal.getSigningKeyId(),
                            seal.getSealSignature()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Evidence protectEvidence(Evidence draft) {
        Objects.requireNonNull(draft, "draft");
        String canonicalPayload = canonicalizer.canonicalizeJson(draft.getPayloadJson());
        Evidence normalized = draft.toBuilder().payloadJson(canonicalPayload).build();
        return normalized.toBuilder()
                .payloadHash(hasher.hash(canonicalEvidence(normalized)))
                .build();
    }

    public boolean verifyEvidence(Evidence evidence) {
        if (evidence == null || evidence.getPayloadHash() == null) {
            return false;
        }
        try {
            String canonicalPayload = canonicalizer.canonicalizeJson(evidence.getPayloadJson());
            Evidence normalized = evidence.toBuilder().payloadJson(canonicalPayload).build();
            return hasher.hash(canonicalEvidence(normalized)).equals(evidence.getPayloadHash());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public TraceIntegrityResult verifyTrace(AgentTrace trace) {
        if (trace == null || trace.getEpisode() == null) {
            return failed("TRACE_MISSING", "AgentTrace不能为空", null, null);
        }
        if (trace.getEpisode().isSealed() != (trace.getSeal() != null)) {
            return failed("SEAL_STATE_MISMATCH", "Episode封印状态与Seal记录不一致", null, null);
        }
        TraceIntegrityResult chain = verifyChain(trace.getEpisode(), trace.getEvents(), trace.getSeal());
        if (!chain.isValid()) {
            return chain;
        }
        for (Evidence item : trace.getEvidence()) {
            if (!verifyEvidence(item)) {
                return failed("EVIDENCE_INTEGRITY_INVALID", "Evidence Hash无效", null, item.getEventCode());
            }
            boolean eventExists = trace.getEvents().stream()
                    .anyMatch(event -> event.getEventCode().equals(item.getEventCode()));
            if (!eventExists || !trace.getEpisode().getEpisodeCode().equals(item.getEpisodeCode())) {
                return failed("EVIDENCE_REFERENCE_INVALID", "Evidence引用了无效的Episode或Event",
                        null, item.getEventCode());
            }
        }
        for (TraceEventRelation relation : trace.getRelations()) {
            boolean sourceExists = trace.getEvents().stream()
                    .anyMatch(event -> event.getEventCode().equals(relation.getSourceEventCode()));
            boolean targetExists = trace.getEvents().stream()
                    .anyMatch(event -> event.getEventCode().equals(relation.getTargetEventCode()));
            if (!sourceExists || !targetExists
                    || !trace.getEpisode().getEpisodeCode().equals(relation.getEpisodeCode())) {
                return failed("RELATION_REFERENCE_INVALID", "Trace关系引用了无效的Episode或Event",
                        null, relation.getTargetEventCode());
            }
        }
        return TraceIntegrityResult.builder().valid(true).build();
    }

    public TraceIntegrityResult verifyChain(AgentEpisode episode, List<TraceEvent> events, EpisodeSeal seal) {
        if (episode == null) {
            return failed("EPISODE_MISSING", "Episode不能为空", null, null);
        }
        List<TraceEvent> safeEvents = events == null ? List.of() : events;
        String previousHash = GENESIS_HASH;
        long expectedSequence = 1;
        for (TraceEvent event : safeEvents) {
            if (event.getSequenceNo() != expectedSequence) {
                return failed("SEQUENCE_MISMATCH", "Trace事件序号不连续",
                        expectedSequence, event.getEventCode());
            }
            if (!episode.getEpisodeCode().equals(event.getEpisodeCode())) {
                return failed("EPISODE_MISMATCH", "Trace事件不属于当前Episode",
                        event.getSequenceNo(), event.getEventCode());
            }
            if (!previousHash.equals(event.getPreviousHash())) {
                return failed("PREVIOUS_HASH_MISMATCH", "Trace事件前序Hash不匹配",
                        event.getSequenceNo(), event.getEventCode());
            }
            if (!verifyEvent(event)) {
                return failed("EVENT_INTEGRITY_INVALID", "Trace事件Hash或签名无效",
                        event.getSequenceNo(), event.getEventCode());
            }
            previousHash = event.getEventHash();
            expectedSequence++;
        }
        if (episode.getEventCount() != safeEvents.size()
                || episode.getNextSequenceNo() != expectedSequence
                || !Objects.equals(episode.getLastEventHash(),
                safeEvents.isEmpty() ? null : safeEvents.get(safeEvents.size() - 1).getEventHash())) {
            return failed("EPISODE_PROJECTION_MISMATCH", "Episode汇总与Trace事件链不一致", null, null);
        }
        if (seal != null && (!verifySeal(seal)
                || seal.getEventCount() != safeEvents.size()
                || seal.getFinalSequenceNo() != expectedSequence - 1
                || !Objects.equals(seal.getFinalEventHash(), episode.getLastEventHash())
                || seal.getFinalStatus() != episode.getStatus())) {
            return failed("SEAL_INTEGRITY_INVALID", "Episode封印与事件链不一致", null, null);
        }
        return TraceIntegrityResult.builder().valid(true).build();
    }

    private String canonicalEvent(TraceEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("capability", event.getCapability());
        fields.put("causationEventCode", event.getCausationEventCode());
        fields.put("correlationId", event.getCorrelationId());
        fields.put("createdBy", event.getCreatedBy());
        fields.put("episodeCode", event.getEpisodeCode());
        fields.put("eventCode", event.getEventCode());
        fields.put("eventType", name(event.getEventType()));
        fields.put("nodeId", event.getNodeId());
        fields.put("occurredAt", text(event.getOccurredAt()));
        fields.put("parentEventCode", event.getParentEventCode());
        fields.put("payloadHash", event.getPayloadHash());
        fields.put("payloadSchemaVersion", event.getPayloadSchemaVersion());
        fields.put("previousHash", event.getPreviousHash());
        fields.put("producerId", event.getProducerId());
        fields.put("reasonCode", event.getReasonCode());
        fields.put("receivedAt", text(event.getReceivedAt()));
        fields.put("sequenceNo", event.getSequenceNo());
        fields.put("stage", name(event.getStage()));
        fields.put("status", name(event.getStatus()));
        fields.put("summary", event.getSummary());
        fields.put("toolName", event.getToolName());
        return canonicalizer.canonicalizeFields(fields);
    }

    private String canonicalEvidence(Evidence evidence) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actualValueRedacted", evidence.getActualValueRedacted());
        fields.put("claim", evidence.getClaim());
        fields.put("collectedAt", text(evidence.getCollectedAt()));
        fields.put("createdAt", text(evidence.getCreatedAt()));
        fields.put("createdBy", evidence.getCreatedBy());
        fields.put("episodeCode", evidence.getEpisodeCode());
        fields.put("eventCode", evidence.getEventCode());
        fields.put("evidenceCode", evidence.getEvidenceCode());
        fields.put("evidenceType", evidence.getEvidenceType());
        fields.put("expectedValueRedacted", evidence.getExpectedValueRedacted());
        fields.put("nodeId", evidence.getNodeId());
        fields.put("payloadJson", evidence.getPayloadJson());
        fields.put("reliabilityLevel", name(evidence.getReliabilityLevel()));
        fields.put("sourceReference", evidence.getSourceReference());
        fields.put("sourceType", evidence.getSourceType());
        fields.put("verificationStatus", name(evidence.getVerificationStatus()));
        return canonicalizer.canonicalizeFields(fields);
    }

    private String eventSignatureMaterial(String episodeCode, long sequenceNo, String eventHash) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("episodeCode", episodeCode);
        fields.put("eventHash", eventHash);
        fields.put("sequenceNo", sequenceNo);
        return canonicalizer.canonicalizeFields(fields);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String text(Instant value) {
        return value == null ? null : value.toString();
    }

    private TraceIntegrityResult failed(String code, String message, Long sequence, String eventCode) {
        return TraceIntegrityResult.builder()
                .valid(false)
                .reasonCode(code)
                .message(message)
                .failedSequenceNo(sequence)
                .failedEventCode(eventCode)
                .build();
    }
}
