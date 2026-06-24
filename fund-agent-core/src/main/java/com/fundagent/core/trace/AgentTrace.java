package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Value
public class AgentTrace {
    AgentEpisode episode;
    List<TraceEvent> events;
    List<TraceEventRelation> relations;
    List<Evidence> evidence;
    EpisodeSeal seal;

    @Builder
    public AgentTrace(AgentEpisode episode, List<TraceEvent> events,
                      List<TraceEventRelation> relations, List<Evidence> evidence, EpisodeSeal seal) {
        this.episode = episode;
        this.events = events == null ? List.of() : List.copyOf(events);
        this.relations = relations == null ? List.of() : List.copyOf(relations);
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.seal = seal;
    }

    public AgentTraceAppendResult append(TraceContext context, AppendTraceEventCommand command,
                                         TraceSecurity security, Instant receivedAt) {
        requireEpisode();
        TraceAppendResult result = episode.append(context, command, security, receivedAt);
        List<TraceEvent> updatedEvents = appendTo(events, result.getEvent());
        List<TraceEventRelation> updatedRelations = appendAll(relations, result.getRelations());
        AgentTrace updated = new AgentTrace(
                result.getEpisode(),
                updatedEvents,
                updatedRelations,
                evidence,
                seal);
        return new AgentTraceAppendResult(updated, result.getEvent(), result.getContext());
    }

    public AgentTrace appendEvidence(AppendEvidenceCommand command, TraceSecurity security, Instant receivedAt) {
        requireEpisode();
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (episode.isSealed()) {
            throw new IllegalStateException("Episode is sealed and cannot append Evidence");
        }
        boolean eventExists = events.stream()
                .anyMatch(event -> event.getEventCode().equals(command.getEventCode()));
        if (!eventExists) {
            throw new IllegalArgumentException("Evidence event does not exist in AgentTrace: "
                    + command.getEventCode());
        }
        Evidence protectedEvidence = security.protectEvidence(Evidence.builder()
                .evidenceCode(command.getEvidenceCode())
                .episodeCode(episode.getEpisodeCode())
                .eventCode(command.getEventCode())
                .nodeId(command.getNodeId())
                .evidenceType(command.getEvidenceType())
                .sourceType(command.getSourceType())
                .sourceReference(command.getSourceReference())
                .claim(command.getClaim())
                .expectedValueRedacted(command.getExpectedValueRedacted())
                .actualValueRedacted(command.getActualValueRedacted())
                .reliabilityLevel(command.getReliabilityLevel())
                .verificationStatus(command.getVerificationStatus())
                .payloadJson(command.getPayloadJson())
                .collectedAt(command.getCollectedAt())
                .createdAt(receivedAt)
                .createdBy(command.getActor())
                .build());
        return new AgentTrace(
                episode,
                events,
                relations,
                appendTo(evidence, protectedEvidence),
                seal);
    }

    public AgentTrace seal(String sealCode, String actor, TraceSecurity security, Instant sealedAt) {
        requireEpisode();
        EpisodeSealResult result = episode.seal(sealCode, actor, security, sealedAt);
        return new AgentTrace(result.getEpisode(), events, relations, evidence, result.getSeal());
    }

    public TraceIntegrityResult verifyIntegrity(TraceSecurity security) {
        requireEpisode();
        return security.verifyTrace(this);
    }

    private void requireEpisode() {
        Objects.requireNonNull(episode, "AgentTrace episode");
    }

    private <T> List<T> appendTo(List<T> source, T item) {
        List<T> result = new ArrayList<>(source);
        result.add(item);
        return List.copyOf(result);
    }

    private <T> List<T> appendAll(List<T> source, List<T> additions) {
        List<T> result = new ArrayList<>(source);
        result.addAll(additions);
        return List.copyOf(result);
    }
}
