package com.fundagent.core.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepRetryPolicy {
    private int maxAttempts;
    private long initialDelayMs;
    private double backoffMultiplier;

    public static StepRetryPolicy noRetry() {
        return new StepRetryPolicy(1, 0, 1.0);
    }

    public static StepRetryPolicy defaultToolRetry() {
        return new StepRetryPolicy(2, 200, 2.0);
    }
}
