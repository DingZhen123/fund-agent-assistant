package com.fundagent.core.dag;

import com.fundagent.core.trace.TraceContext;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DagExecutionContext {
    private String userId;
    private String conversationId;
    private String userMessage;
    private String dagId;
    private TraceContext traceContext;
}
