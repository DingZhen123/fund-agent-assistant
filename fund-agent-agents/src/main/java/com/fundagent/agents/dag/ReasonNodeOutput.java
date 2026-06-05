package com.fundagent.agents.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ReasonNodeOutput {
    private String summary;

    @JSONField(name = "outputs_json")
    private String outputsJson;
}
