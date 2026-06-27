package com.fundagent.agents.dag;

import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagGraphState;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.NodeType;
import com.fundagent.core.llm.AgentLLMService;
import com.fundagent.core.llm.LLMResponse;
import com.fundagent.core.trace.TraceContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerNodeExecutorTraceTest {

    @Test
    void usesAgentLlmAndAdvancesTraceContext() {
        TraceContext initial = TraceContext.builder()
                .episodeCode("EP_TEST")
                .currentEventCode("EV_NODE_STARTED")
                .causationEventCode("EV_NODE_STARTED")
                .correlationId("REQ_TEST")
                .requestId("REQ_TEST")
                .build();
        TraceContext advanced = initial.childOf("EV_MODEL_CALL_COMPLETED");
        RecordingAgentLLMService llm = new RecordingAgentLLMService(advanced);
        AnswerNodeExecutor executor = new AnswerNodeExecutor(llm);

        DagExecutionContext context = DagExecutionContext.builder()
                .userMessage("hello")
                .traceContext(initial)
                .build();

        NodeExecutionResult result = executor.execute(answerNode(), new DagGraphState(), context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getTraceContext()).isEqualTo(advanced);
        assertThat(llm.lastRequest.getTraceContext()).isEqualTo(initial);
        assertThat(llm.lastRequest.getCallerName()).isEqualTo("AnswerNodeExecutor");
        assertThat(llm.lastRequest.getNodeId()).isEqualTo("answer-1");
        assertThat(llm.lastRequest.getCapability()).isEqualTo("conversation.answer");
    }

    private BoundDagNode answerNode() {
        BoundDagNode node = new BoundDagNode();
        node.setNodeId("answer-1");
        node.setNodeType(NodeType.FINAL_ANSWER);
        node.setCapability("conversation.answer");
        node.setInstruction("answer");
        return node;
    }

    private static class RecordingAgentLLMService implements AgentLLMService {
        private final TraceContext responseContext;
        private com.fundagent.core.llm.LLMRequest lastRequest;

        private RecordingAgentLLMService(TraceContext responseContext) {
            this.responseContext = responseContext;
        }

        @Override
        public LLMResponse call(com.fundagent.core.llm.LLMRequest request) {
            this.lastRequest = request;
            return LLMResponse.builder()
                    .content("{\"answer\":\"ok\"}")
                    .provider("test")
                    .model("test-model")
                    .traceContext(responseContext)
                    .build();
        }
    }
}
