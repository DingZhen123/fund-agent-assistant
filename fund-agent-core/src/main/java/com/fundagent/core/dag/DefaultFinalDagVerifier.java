package com.fundagent.core.dag;

public class DefaultFinalDagVerifier implements FinalDagVerifier {

    @Override
    public FinalVerificationResult verify(BoundDagPlan plan, DagRunResult runResult) {
        if (plan == null) {
            return FinalVerificationResult.failed("BOUND_DAG_NULL", "绑定后的DAG为空");
        }
        if (runResult == null) {
            return FinalVerificationResult.failed("DAG_RUN_RESULT_NULL", "DAG运行结果为空");
        }
        if (!DagRunStatus.COMPLETED.equals(runResult.getStatus())) {
            return FinalVerificationResult.failed("DAG_NOT_COMPLETED",
                    "DAG未完成，当前状态: " + runResult.getStatus());
        }
        if (runResult.getState() == null || runResult.getState().getObservations() == null
                || runResult.getState().getObservations().isEmpty()) {
            return FinalVerificationResult.failed("OBSERVATIONS_EMPTY", "DAG没有任何Observation");
        }

        BoundDagNode finalNode = findFinalAnswerNode(plan);
        if (finalNode == null) {
            return FinalVerificationResult.failed("FINAL_ANSWER_NODE_MISSING", "DAG缺少FINAL_ANSWER节点");
        }

        NodeObservation finalObservation = runResult.getState().getObservations().get(finalNode.getNodeId());
        if (finalObservation == null) {
            return FinalVerificationResult.failed("FINAL_ANSWER_OBSERVATION_MISSING",
                    "缺少FINAL_ANSWER Observation: " + finalNode.getNodeId());
        }
        if (!NodeExecutionStatus.SUCCESS.equals(finalObservation.getStatus())) {
            return FinalVerificationResult.failed("FINAL_ANSWER_NOT_SUCCESS",
                    "FINAL_ANSWER节点未成功完成: " + finalNode.getNodeId());
        }

        Object answer = finalObservation.getOutputs() != null ? finalObservation.getOutputs().get("answer") : null;
        if (answer == null || answer.toString().trim().isEmpty()) {
            return FinalVerificationResult.failed("FINAL_ANSWER_EMPTY", "最终回答不能为空");
        }
        return FinalVerificationResult.passed(answer.toString());
    }

    private BoundDagNode findFinalAnswerNode(BoundDagPlan plan) {
        if (plan.getNodes() == null) {
            return null;
        }
        return plan.getNodes().stream()
                .filter(node -> NodeType.FINAL_ANSWER.equals(node.getNodeType()))
                .findFirst()
                .orElse(null);
    }
}
