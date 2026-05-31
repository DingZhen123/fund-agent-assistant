package com.fundagent.core.routing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedTaskRouter implements TaskRouter {
    private static final Pattern NOTE_CODE_PATTERN = Pattern.compile("\\b(?:EC|LA|TR|FP|YFKD)[A-Za-z0-9_-]+\\b");
    private static final List<String> ACTION_KEYWORDS = List.of(
            "查询", "查一下", "查", "发送", "发", "获取", "下载", "分析", "汇总", "总结", "生成", "对比", "判断");
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "如果", "若", "假如", "当", "然后", "并且", "同时", "再", "分别", "批量", "多个", "全部", "这些",
            "分析", "原因", "汇总", "总结", "报告", "说明", "归因", "对比", "最近三个月");
    private static final List<String> GREETING_KEYWORDS = List.of("你好", "您好", "hello", "hi");
    private static final List<String> MEMORY_KEYWORDS = List.of("上一个问题", "刚才", "之前", "我问过", "上一条");

    @Override
    public TaskRouteResult route(String userMessage) {
        String message = userMessage != null ? userMessage.trim() : "";
        List<String> matchedRules = new ArrayList<>();

        if (message.isEmpty()) {
            matchedRules.add("empty_message");
            return TaskRouteResult.simple(0.95, "空消息不需要复杂任务图", matchedRules);
        }

        if (containsAnyIgnoreCase(message, GREETING_KEYWORDS)) {
            matchedRules.add("greeting");
            return TaskRouteResult.simple(0.95, "问候类消息走简单链路", matchedRules);
        }

        if (containsAny(message, MEMORY_KEYWORDS)) {
            matchedRules.add("memory_question");
            return TaskRouteResult.simple(0.9, "历史上下文问答走简单链路", matchedRules);
        }

        int noteCodeCount = countNoteCodes(message);
        if (noteCodeCount >= 2) {
            matchedRules.add("multiple_business_objects");
        }

        List<String> complexKeywordHits = collectHits(message, COMPLEX_KEYWORDS);
        if (!complexKeywordHits.isEmpty()) {
            matchedRules.add("complex_keywords:" + String.join(",", complexKeywordHits));
        }

        Set<String> actionHits = new LinkedHashSet<>(collectHits(message, ACTION_KEYWORDS));
        if (actionHits.size() >= 2) {
            matchedRules.add("multiple_actions:" + String.join(",", actionHits));
        }

        if (matchedRules.stream().anyMatch(rule -> rule.startsWith("complex_keywords")
                || rule.startsWith("multiple_actions")
                || rule.equals("multiple_business_objects"))) {
            return TaskRouteResult.complex(0.85, "命中复杂任务规则", matchedRules);
        }

        if (noteCodeCount == 1 && actionHits.size() <= 1) {
            matchedRules.add("single_business_object");
            return TaskRouteResult.simple(0.85, "单业务对象且动作简单", matchedRules);
        }

        if (message.length() > 80) {
            matchedRules.add("long_uncertain_message");
            return TaskRouteResult.needClassification("长文本但未命中明确复杂规则，建议后续交给LLM分类", matchedRules);
        }

        matchedRules.add("default_simple");
        return TaskRouteResult.simple(0.65, "未命中复杂规则，默认走简单链路", matchedRules);
    }

    private int countNoteCodes(String message) {
        Matcher matcher = NOTE_CODE_PATTERN.matcher(message);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private List<String> collectHits(String message, List<String> keywords) {
        List<String> hits = new ArrayList<>();
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                hits.add(keyword);
            }
        }
        return hits;
    }

    private boolean containsAny(String message, List<String> keywords) {
        return !collectHits(message, keywords).isEmpty();
    }

    private boolean containsAnyIgnoreCase(String message, List<String> keywords) {
        String lower = message.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
