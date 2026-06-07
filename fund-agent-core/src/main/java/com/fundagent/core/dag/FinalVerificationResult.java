package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FinalVerificationResult {
    private boolean passed;

    @JSONField(name = "error_code")
    private String errorCode;

    private String message;

    @JSONField(name = "final_answer")
    private String finalAnswer;

    public static FinalVerificationResult passed(String finalAnswer) {
        return new FinalVerificationResult(true, null, null, finalAnswer);
    }

    public static FinalVerificationResult failed(String errorCode, String message) {
        return new FinalVerificationResult(false, errorCode, message, null);
    }
}
