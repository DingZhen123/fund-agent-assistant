package com.fundagent.repo.trace;

import com.fundagent.core.trace.DefaultTraceCanonicalizer;
import com.fundagent.core.trace.HmacSha256TraceSigner;
import com.fundagent.core.trace.Sha256TraceHasher;
import com.fundagent.core.trace.TraceSecurity;
import com.fundagent.repo.mapper.AgentEpisodeMapper;
import com.fundagent.repo.mapper.EpisodeSealMapper;
import com.fundagent.repo.mapper.TraceEventMapper;
import com.fundagent.repo.mapper.TraceEventRelationMapper;
import com.fundagent.repo.mapper.TraceEvidenceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(prefix = "agent.trace", name = "enabled", havingValue = "true")
public class TracePersistenceConfiguration {

    @Bean
    public TraceEntityConverter traceEntityConverter() {
        return new TraceEntityConverter();
    }

    @Bean
    public Clock traceClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TraceSecurity traceSecurity(
            @Value("${agent.trace.signing-key-id}") String signingKeyId,
            @Value("${agent.trace.signing-key-base64}") String signingKeyBase64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(signingKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("agent.trace.signing-key-base64 must be valid Base64", e);
        }
        return new TraceSecurity(
                new DefaultTraceCanonicalizer(),
                new Sha256TraceHasher(),
                new HmacSha256TraceSigner(signingKeyId, key));
    }

    @Bean
    public PersistentTraceStore persistentTraceStore(
            AgentEpisodeMapper episodeMapper,
            TraceEventMapper eventMapper,
            TraceEventRelationMapper relationMapper,
            TraceEvidenceMapper evidenceMapper,
            EpisodeSealMapper sealMapper,
            TraceEntityConverter converter,
            TraceSecurity security,
            Clock traceClock) {
        return new PersistentTraceStore(
                episodeMapper,
                eventMapper,
                relationMapper,
                evidenceMapper,
                sealMapper,
                converter,
                security,
                traceClock);
    }
}
