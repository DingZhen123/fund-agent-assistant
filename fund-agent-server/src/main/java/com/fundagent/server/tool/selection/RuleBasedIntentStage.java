package com.fundagent.server.tool.selection;

import com.fundagent.core.tool.selection.ToolSelectionContext;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(200)
public class RuleBasedIntentStage implements ToolSelectionStage {
    private static final List<String> QUERY_KEYWORDS =
            List.of("查询", "查一下", "查", "看一下", "获取");
    private static final List<String> SEND_KEYWORDS =
            List.of("发送", "发我", "发给", "推送");
    private static final List<String> DOWNLOAD_KEYWORDS =
            List.of("下载", "导出");
    private static final List<String> ANALYZE_KEYWORDS =
            List.of("分析", "汇总", "总结", "对比", "判断");
    private static final List<String> CREATE_KEYWORDS =
            List.of("创建", "新建", "提交", "发起");
    private static final List<String> UPDATE_KEYWORDS =
            List.of("修改", "更新", "变更");

    @Override
    public void apply(ToolSelectionContext context) {
        String message = context.getUserMessage();
        addIntentIfMatched(context, message, "query", QUERY_KEYWORDS);
        addIntentIfMatched(context, message, "send", SEND_KEYWORDS);
        addIntentIfMatched(context, message, "download", DOWNLOAD_KEYWORDS);
        addIntentIfMatched(context, message, "analyze", ANALYZE_KEYWORDS);
        addIntentIfMatched(context, message, "create", CREATE_KEYWORDS);
        addIntentIfMatched(context, message, "update", UPDATE_KEYWORDS);
        if (context.getIntents().isEmpty()) {
            context.addMatchedRule("intent:unknown");
        }
    }

    private void addIntentIfMatched(ToolSelectionContext context, String message,
                                    String intent, List<String> keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                context.addIntent(intent);
                context.addMatchedRule("intent:" + intent);
                return;
            }
        }
    }
}
