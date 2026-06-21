package com.fundagent.server.promptlab;

import com.fundagent.core.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PromptLabService {
    private final LLMService llmService;

    public PromptLabService(LLMService llmService) {
        this.llmService = llmService;
    }

    public PromptTestResult run(PromptLabRequest request) {
        validatePrompt(request);
        PromptTestCase testCase = new PromptTestCase();
        testCase.setName("single");
        testCase.setInput(request.getInput());
        testCase.setExpectedContains(request.getExpectedContains());
        testCase.setForbiddenContains(request.getForbiddenContains());
        return execute(request, testCase);
    }

    public PromptBatchResult runBatch(PromptLabRequest request) {
        validatePrompt(request);
        if (request.getCases() == null || request.getCases().isEmpty()) {
            throw new IllegalArgumentException("cases不能为空");
        }

        long start = System.currentTimeMillis();
        List<PromptTestResult> results = request.getCases().stream()
                .map(testCase -> execute(request, testCase))
                .toList();
        int passed = (int) results.stream().filter(PromptTestResult::isPassed).count();
        return PromptBatchResult.builder()
                .total(results.size())
                .passed(passed)
                .passRate(results.isEmpty() ? 0.0D : (double) passed / results.size())
                .elapsedMs(System.currentTimeMillis() - start)
                .results(results)
                .build();
    }

    private PromptTestResult execute(PromptLabRequest request, PromptTestCase testCase) {
        if (testCase == null || isBlank(testCase.getInput())) {
            throw new IllegalArgumentException("测试用例input不能为空");
        }

        long start = System.currentTimeMillis();
        String output = hasSchema(request)
                ? llmService.chatStructured(
                        request.getSystemPrompt(),
                        List.of(),
                        testCase.getInput(),
                        request.getSchemaName(),
                        request.getSchemaJson())
                : llmService.chat(request.getSystemPrompt(), List.of(), testCase.getInput());
        long elapsedMs = System.currentTimeMillis() - start;

        List<PromptAssertionResult> assertions = evaluate(
                output,
                testCase.getExpectedContains(),
                testCase.getForbiddenContains());
        boolean passed = assertions.stream().allMatch(PromptAssertionResult::isPassed);
        log.info("Prompt test completed: name={}, passed={}, elapsedMs={}, assertionCount={}",
                testCase.getName(), passed, elapsedMs, assertions.size());

        return PromptTestResult.builder()
                .name(isBlank(testCase.getName()) ? "unnamed" : testCase.getName())
                .input(testCase.getInput())
                .output(output)
                .elapsedMs(elapsedMs)
                .passed(passed)
                .assertions(assertions)
                .build();
    }

    private List<PromptAssertionResult> evaluate(String output,
                                                 List<String> expectedContains,
                                                 List<String> forbiddenContains) {
        String safeOutput = output != null ? output : "";
        List<PromptAssertionResult> results = new ArrayList<>();
        if (expectedContains != null) {
            for (String expected : expectedContains) {
                if (!isBlank(expected)) {
                    results.add(new PromptAssertionResult(
                            "CONTAINS", expected, safeOutput.contains(expected)));
                }
            }
        }
        if (forbiddenContains != null) {
            for (String forbidden : forbiddenContains) {
                if (!isBlank(forbidden)) {
                    results.add(new PromptAssertionResult(
                            "NOT_CONTAINS", forbidden, !safeOutput.contains(forbidden)));
                }
            }
        }
        return results;
    }

    private void validatePrompt(PromptLabRequest request) {
        if (request == null || isBlank(request.getSystemPrompt())) {
            throw new IllegalArgumentException("systemPrompt不能为空");
        }
        if ((isBlank(request.getSchemaName()) && !isBlank(request.getSchemaJson()))
                || (!isBlank(request.getSchemaName()) && isBlank(request.getSchemaJson()))) {
            throw new IllegalArgumentException("schemaName和schemaJson必须同时提供");
        }
    }

    private boolean hasSchema(PromptLabRequest request) {
        return !isBlank(request.getSchemaName()) && !isBlank(request.getSchemaJson());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
