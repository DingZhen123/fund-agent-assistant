package com.fundagent.core.dag;

public interface FinalDagVerifier {
    FinalVerificationResult verify(BoundDagPlan plan, DagRunResult runResult);
}
