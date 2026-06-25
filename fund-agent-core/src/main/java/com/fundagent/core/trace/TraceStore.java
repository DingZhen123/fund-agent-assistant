package com.fundagent.core.trace;

public interface TraceStore {

    TraceAppendResult createEpisode(CreateEpisodeCommand command);

    TraceAppendResult append(TraceContext context, AppendTraceEventCommand command);

    Evidence appendEvidence(TraceContext context, AppendEvidenceCommand command);

    EpisodeSeal sealEpisode(String episodeCode, EpisodeStatus finalStatus, String actor);

    AgentTrace loadTrace(String episodeCode);

    TraceIntegrityResult verifyIntegrity(String episodeCode);
}
