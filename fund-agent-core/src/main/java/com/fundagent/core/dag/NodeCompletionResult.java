package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeCompletionResult {
    private boolean passed;

    @JSONField(name = "error_code")
    private String errorCode;

    private String message;

    private boolean retryable;

    @JSONField(name = "need_replan")
    private boolean needReplan;

    @JSONField(name = "waiting_user_input")
    private boolean waitingUserInput;

    public static NodeCompletionResult passed() {
        return new NodeCompletionResult(true, null, null, false, false, false);
    }

    public static NodeCompletionResult waiting(String message) {
        return new NodeCompletionResult(true, null, message, false, false, true);
    }

    public static NodeCompletionResult failed(String errorCode, String message) {
        return new NodeCompletionResult(false, errorCode, message, false, false, false);
    }
}
