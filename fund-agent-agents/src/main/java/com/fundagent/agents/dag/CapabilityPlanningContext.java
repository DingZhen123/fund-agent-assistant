package com.fundagent.agents.dag;

import com.fundagent.core.capability.CapabilityDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CapabilityPlanningContext {
    private List<CapabilityDefinition> capabilities;
    private String capabilitiesDescription;
    private String dagPlanSchema;
}
