package com.fundagent.server.controller;

import com.fundagent.server.promptlab.PromptBatchResult;
import com.fundagent.server.promptlab.PromptLabRequest;
import com.fundagent.server.promptlab.PromptLabService;
import com.fundagent.server.promptlab.PromptTestResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/prompt-lab")
@CrossOrigin("*")
public class PromptLabController {
    private final PromptLabService promptLabService;

    public PromptLabController(PromptLabService promptLabService) {
        this.promptLabService = promptLabService;
    }

    @PostMapping("/run")
    public PromptTestResult run(@RequestBody PromptLabRequest request) {
        return promptLabService.run(request);
    }

    @PostMapping("/batch")
    public PromptBatchResult batch(@RequestBody PromptLabRequest request) {
        return promptLabService.runBatch(request);
    }
}
