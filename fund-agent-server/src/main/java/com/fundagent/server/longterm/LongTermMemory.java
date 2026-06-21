package com.fundagent.server.longterm;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class LongTermMemory {
    private String id;
    private String memory;
    private double score;
    private Map<String, Object> metadata;
}
