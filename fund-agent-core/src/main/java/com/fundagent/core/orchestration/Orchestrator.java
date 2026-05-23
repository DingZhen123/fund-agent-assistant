package com.fundagent.core.orchestration;

import com.fundagent.core.agent.Agent;
import com.fundagent.core.agent.AgentRegistry;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.Round;
import com.fundagent.core.post.Post;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class Orchestrator {
    private final AgentRegistry agentRegistry;
    private final int maxInternalRounds;

    public Orchestrator(AgentRegistry agentRegistry, int maxInternalRounds) {
        this.agentRegistry = agentRegistry;
        this.maxInternalRounds = maxInternalRounds;
    }

    public OrchestrationResult processMessage(Memory memory, String userMessage,
                                               Consumer<OrchestrationEvent> eventListener) {
        return processMessage(memory, userMessage, eventListener, null);
    }

    public OrchestrationResult processMessage(Memory memory, String userMessage,
                                               Consumer<OrchestrationEvent> eventListener,
                                               Consumer<String> onToken) {
        long startTime = System.currentTimeMillis();
        List<Post> allPosts = new ArrayList<>();
        OrchestrationResult result = new OrchestrationResult();

        Round round = memory.newRound(userMessage);
        Post userPost = Post.create("User", "Planner", userMessage);
        round.addPost(userPost);
        allPosts.add(userPost);

        emit(eventListener, OrchestrationEventType.ROUND_START, "Planner", "开始规划任务...");

        Post currentPost = userPost;
        int internalCount = 0;

        while (internalCount < maxInternalRounds) {
            String sendTo = currentPost.getSendTo();

            if ("User".equals(sendTo)) {
                round.markCompleted();
                result.setFinalAnswer(currentPost.getMessage());
                result.setAllPosts(allPosts);
                result.setElapsedMs(System.currentTimeMillis() - startTime);
                emit(eventListener, OrchestrationEventType.MESSAGE_END, "Planner", currentPost.getMessage());
                return result;
            }

            Agent agent = agentRegistry.getInstance(sendTo);
            if (agent == null) {
                log.error("Agent not found: {}", sendTo);
                result.setFinalAnswer("系统错误：Agent " + sendTo + " 未注册");
                result.setAllPosts(allPosts);
                return result;
            }

            log.info("{} → {}: {}", currentPost.getSendFrom(), sendTo,
                    currentPost.getMessage() != null
                            ? currentPost.getMessage().substring(0, Math.min(50, currentPost.getMessage().length()))
                            : "");

            emit(eventListener, OrchestrationEventType.AGENT_START, sendTo, sendTo + " 开始处理...");

            Post response = onToken != null
                    ? agent.reply(memory, currentPost, onToken)
                    : agent.reply(memory, currentPost);
            if (response == null) {
                result.setFinalAnswer("系统错误：Agent " + sendTo + " 返回空响应");
                result.setAllPosts(allPosts);
                return result;
            }

            round.addPost(response);
            allPosts.add(response);

            emit(eventListener, OrchestrationEventType.AGENT_END, sendTo, response.getMessage());
            currentPost = response;
            internalCount++;
        }

        result.setFinalAnswer("系统处理超时，请稍后重试");
        result.setAllPosts(allPosts);
        emit(eventListener, OrchestrationEventType.ERROR, "System", "处理超时");
        return result;
    }

    private void emit(Consumer<OrchestrationEvent> listener,
                      OrchestrationEventType type, String agent, String message) {
        if (listener != null) {
            listener.accept(new OrchestrationEvent(type, agent, message));
        }
    }
}
