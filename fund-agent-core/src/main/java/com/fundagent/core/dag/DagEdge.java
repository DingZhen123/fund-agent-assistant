package com.fundagent.core.dag;

import lombok.Data;

@Data
public class DagEdge {
    private String from;
    private String to;
    private String condition;
}
