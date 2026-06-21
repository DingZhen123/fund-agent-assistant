package com.fundagent.server.longterm;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class LongTermMemoryRememberResult {
    private boolean success;
    private String provider;
    private String message;
    private Map<String, Object> raw;
}
