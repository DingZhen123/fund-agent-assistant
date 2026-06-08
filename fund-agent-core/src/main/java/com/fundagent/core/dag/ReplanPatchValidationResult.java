package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplanPatchValidationResult {
    private boolean valid;

    @JSONField(name = "error_code")
    private String errorCode;

    private String message;

    public static ReplanPatchValidationResult ok() {
        return new ReplanPatchValidationResult(true, null, null);
    }

    public static ReplanPatchValidationResult error(String errorCode, String message) {
        return new ReplanPatchValidationResult(false, errorCode, message);
    }
}
