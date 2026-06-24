package com.fundagent.core.trace;

public enum TraceStage {
    EPISODE,
    CONTEXT,
    PLANNING,
    PLAN_VALIDATION,
    TOOL_BINDING,
    SCHEDULING,
    NODE_EXECUTION,
    MODEL_CALL,
    POLICY,
    CONFIRMATION,
    TOOL_CALL,
    EVIDENCE,
    VERIFICATION,
    RECOVERY,
    SEALING
}
