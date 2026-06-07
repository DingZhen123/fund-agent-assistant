package com.fundagent.core.dag;

import java.util.Map;

public class DefaultNodeCompletionChecker implements NodeCompletionChecker {

    @Override
    public NodeCompletionResult check(BoundDagNode node, NodeExecutionResult result, DagGraphState state) {
        if (node == null) {
            return NodeCompletionResult.failed("NODE_NULL", "节点不能为空");
        }
        if (result == null) {
            return NodeCompletionResult.failed("NODE_RESULT_NULL", "节点执行结果不能为空: " + node.getNodeId());
        }
        NodeObservation observation = result.getObservation();
        if (observation == null) {
            return NodeCompletionResult.failed("OBSERVATION_NULL", "节点Observation不能为空: " + node.getNodeId());
        }
        if (!node.getNodeId().equals(observation.getNodeId())) {
            return NodeCompletionResult.failed("OBSERVATION_NODE_MISMATCH",
                    "Observation节点不匹配: " + node.getNodeId() + " != " + observation.getNodeId());
        }
        if (observation.getStatus() == null) {
            return NodeCompletionResult.failed("OBSERVATION_STATUS_MISSING",
                    "Observation缺少status: " + node.getNodeId());
        }

        return switch (node.getNodeType()) {
            case QUERY -> checkQueryNode(node, result, observation);
            case ACTION -> checkActionNode(node, result, observation);
            case LLM_REASON -> checkReasonNode(node, observation);
            case FINAL_ANSWER -> checkFinalAnswerNode(node, observation);
            case ASK_USER -> checkAskUserNode(node, result, observation);
            case VERIFY -> checkVerifyNode(node, observation);
        };
    }

    private NodeCompletionResult checkQueryNode(BoundDagNode node, NodeExecutionResult result,
                                                NodeObservation observation) {
        if (!result.isSuccess() || !NodeExecutionStatus.SUCCESS.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("QUERY_NODE_NOT_SUCCESS",
                    "QUERY节点未成功完成: " + node.getNodeId());
        }
        if (isEmpty(observation.getOutputs())) {
            return NodeCompletionResult.failed("QUERY_OUTPUTS_EMPTY",
                    "QUERY节点outputs不能为空: " + node.getNodeId());
        }
        return NodeCompletionResult.passed();
    }

    private NodeCompletionResult checkActionNode(BoundDagNode node, NodeExecutionResult result,
                                                 NodeObservation observation) {
        if (NodeExecutionStatus.SKIPPED.equals(observation.getStatus())) {
            return isBlank(observation.getSummary())
                    ? NodeCompletionResult.failed("ACTION_SKIP_REASON_MISSING",
                    "ACTION节点跳过时必须说明原因: " + node.getNodeId())
                    : NodeCompletionResult.passed();
        }
        if (!result.isSuccess() || !NodeExecutionStatus.SUCCESS.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("ACTION_NODE_NOT_SUCCESS",
                    "ACTION节点未成功完成: " + node.getNodeId());
        }
        if (isEmpty(observation.getOutputs()) && (observation.getToolCalls() == null || observation.getToolCalls().isEmpty())) {
            return NodeCompletionResult.failed("ACTION_RESULT_EMPTY",
                    "ACTION节点成功时outputs或tool_calls不能为空: " + node.getNodeId());
        }
        return NodeCompletionResult.passed();
    }

    private NodeCompletionResult checkReasonNode(BoundDagNode node, NodeObservation observation) {
        if (!NodeExecutionStatus.SUCCESS.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("REASON_NODE_NOT_SUCCESS",
                    "LLM_REASON节点未成功完成: " + node.getNodeId());
        }
        if (isBlank(observation.getSummary())) {
            return NodeCompletionResult.failed("REASON_SUMMARY_MISSING",
                    "LLM_REASON节点summary不能为空: " + node.getNodeId());
        }
        return NodeCompletionResult.passed();
    }

    private NodeCompletionResult checkFinalAnswerNode(BoundDagNode node, NodeObservation observation) {
        if (!NodeExecutionStatus.SUCCESS.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("FINAL_ANSWER_NOT_SUCCESS",
                    "FINAL_ANSWER节点未成功完成: " + node.getNodeId());
        }
        Object answer = observation.getOutputs() != null ? observation.getOutputs().get("answer") : null;
        if (answer == null || answer.toString().trim().isEmpty()) {
            return NodeCompletionResult.failed("FINAL_ANSWER_MISSING",
                    "FINAL_ANSWER节点必须输出answer: " + node.getNodeId());
        }
        return NodeCompletionResult.passed();
    }

    private NodeCompletionResult checkAskUserNode(BoundDagNode node, NodeExecutionResult result,
                                                  NodeObservation observation) {
        if (!result.isWaitingUserInput()
                || !NodeExecutionStatus.WAITING_USER_INPUT.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("ASK_USER_NOT_WAITING",
                    "ASK_USER节点必须进入等待用户输入状态: " + node.getNodeId());
        }
        Object question = observation.getOutputs() != null ? observation.getOutputs().get("question") : null;
        if ((question == null || question.toString().trim().isEmpty()) && isBlank(observation.getSummary())) {
            return NodeCompletionResult.failed("ASK_USER_QUESTION_MISSING",
                    "ASK_USER节点必须输出问题: " + node.getNodeId());
        }
        return NodeCompletionResult.waiting(observation.getSummary());
    }

    private NodeCompletionResult checkVerifyNode(BoundDagNode node, NodeObservation observation) {
        if (!NodeExecutionStatus.SUCCESS.equals(observation.getStatus())) {
            return NodeCompletionResult.failed("VERIFY_NODE_NOT_SUCCESS",
                    "VERIFY节点未成功完成: " + node.getNodeId());
        }
        return NodeCompletionResult.passed();
    }

    private boolean isEmpty(Map<String, Object> value) {
        return value == null || value.isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
