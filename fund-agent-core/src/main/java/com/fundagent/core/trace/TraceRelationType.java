package com.fundagent.core.trace;

public enum TraceRelationType {
    CAUSED_BY,
    DEPENDS_ON,
    RETRY_OF,
    REPLAN_OF,
    VERIFIES,
    PRODUCED_EVIDENCE,
    RECOVERS_FROM
}
