package com.fundagent.core.dag;

import com.fundagent.core.trace.AgentTrace;
import com.fundagent.core.trace.AppendEvidenceCommand;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.EpisodeSeal;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.Evidence;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceIntegrityResult;
import com.fundagent.core.trace.TraceStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DagRuntimeTraceTest {

    @Test
    void recordsNodeEventsAndAdvancesTraceContext() {
        RecordingTraceStore traceStore = new RecordingTraceStore();
        DagRuntime runtime = new DagRuntime(
                new DefaultNodeRouter(List.of(new FinalAnswerExecutor())),
                new DefaultNodeCompletionChecker(),
                new DefaultFinalDagVerifier(),
                traceStore);

        TraceContext initialContext = TraceContext.builder()
                .episodeCode("EP_TEST")
                .currentEventCode("EV_EPISODE_CREATED")
                .causationEventCode("EV_EPISODE_CREATED")
                .correlationId("REQ_TEST")
                .requestId("REQ_TEST")
                .build();
        DagExecutionContext context = DagExecutionContext.builder()
                .dagId("dag-1")
                .conversationId("conv-1")
                .userId("user-1")
                .userMessage("hello")
                .traceContext(initialContext)
                .build();

        DagRunResult result = runtime.run(plan(), context);

        assertThat(result.getStatus()).isEqualTo(DagRunStatus.COMPLETED);
        assertThat(traceStore.commands)
                .extracting(AppendTraceEventCommand::getEventType)
                .containsExactly(TraceEventType.NODE_STARTED, TraceEventType.NODE_COMPLETED);
        assertThat(traceStore.commands)
                .extracting(AppendTraceEventCommand::getStatus)
                .containsExactly(TraceEventStatus.STARTED, TraceEventStatus.SUCCEEDED);
        assertThat(context.getTraceContext().getCurrentEventCode())
                .isEqualTo(traceStore.commands.get(1).getEventCode());
        assertThat(context.getTraceContext().getCausationEventCode())
                .isEqualTo(traceStore.commands.get(0).getEventCode());
    }

    private BoundDagPlan plan() {
        BoundDagNode node = new BoundDagNode();
        node.setNodeId("answer-1");
        node.setName("answer");
        node.setNodeType(NodeType.FINAL_ANSWER);
        node.setCapability("conversation.answer");
        node.setInstruction("answer user");

        BoundDagPlan plan = new BoundDagPlan();
        plan.setDagId("dag-1");
        plan.setGoal("answer");
        plan.setNodes(List.of(node));
        return plan;
    }

    private static class FinalAnswerExecutor implements NodeExecutor {
        @Override
        public boolean supports(BoundDagNode node) {
            return node != null && NodeType.FINAL_ANSWER.equals(node.getNodeType());
        }

        @Override
        public NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
            NodeObservation observation = NodeObservation.builder()
                    .nodeId(node.getNodeId())
                    .nodeType(node.getNodeType())
                    .capability(node.getCapability())
                    .status(NodeExecutionStatus.SUCCESS)
                    .summary("ok")
                    .outputs(Map.of("answer", "ok"))
                    .build();
            return NodeExecutionResult.success(observation);
        }
    }

    private static class RecordingTraceStore implements TraceStore {
        private final List<AppendTraceEventCommand> commands = new ArrayList<>();

        @Override
        public TraceAppendResult createEpisode(CreateEpisodeCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TraceAppendResult append(TraceContext context, AppendTraceEventCommand command) {
            commands.add(command);
            return TraceAppendResult.builder()
                    .context(context.childOf(command.getEventCode()))
                    .build();
        }

        @Override
        public Evidence appendEvidence(TraceContext context, AppendEvidenceCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EpisodeSeal sealEpisode(String episodeCode, EpisodeStatus finalStatus, String actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentTrace loadTrace(String episodeCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TraceIntegrityResult verifyIntegrity(String episodeCode) {
            throw new UnsupportedOperationException();
        }
    }
}
