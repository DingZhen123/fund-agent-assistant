package com.fundagent.server.controller;

import com.alibaba.fastjson2.JSONObject;
import com.fundagent.server.dto.CapabilityDagDebugResult;
import com.fundagent.server.dto.CapabilityDagReplanDebugResult;
import com.fundagent.server.dto.CapabilityDagRunWithReplanDebugResult;
import com.fundagent.server.service.CapabilityDagDebugService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/debug/capability-dag")
@CrossOrigin("*")
public class CapabilityDagDebugController {
    private final CapabilityDagDebugService capabilityDagDebugService;

    public CapabilityDagDebugController(CapabilityDagDebugService capabilityDagDebugService) {
        this.capabilityDagDebugService = capabilityDagDebugService;
    }

    @PostMapping("/plan")
    public CapabilityDagDebugResult plan(@RequestBody JSONObject request) {
        String conversationId = request.getString("conversationId");
        String message = request.getString("message");
        log.info("debug capability dag plan: conversationId={}, message={}", conversationId, message);
        return capabilityDagDebugService.plan(conversationId, message);
    }

    @PostMapping("/run")
    public CapabilityDagDebugResult run(@RequestBody JSONObject request) {
        String conversationId = request.getString("conversationId");
        String message = request.getString("message");
        String userId = request.getString("userId");
        log.info("debug capability dag run: conversationId={}, userId={}, message={}",
                conversationId, userId, message);
        return capabilityDagDebugService.run(conversationId, message,
                userId != null && !userId.isBlank() ? userId : "debug-user");
    }

    @PostMapping("/run-with-replan")
    public CapabilityDagRunWithReplanDebugResult runWithReplan(@RequestBody JSONObject request) {
        String conversationId = request.getString("conversationId");
        String message = request.getString("message");
        String userId = request.getString("userId");
        log.info("debug capability dag run with replan: conversationId={}, userId={}, message={}",
                conversationId, userId, message);
        return capabilityDagDebugService.runWithReplan(conversationId, message,
                userId != null && !userId.isBlank() ? userId : "debug-user");
    }

    @PostMapping("/replan")
    public CapabilityDagReplanDebugResult replan(@RequestBody JSONObject request) {
        String conversationId = request.getString("conversationId");
        String message = request.getString("message");
        String userId = request.getString("userId");
        log.info("debug capability dag replan: conversationId={}, userId={}, message={}",
                conversationId, userId, message);
        return capabilityDagDebugService.replan(conversationId, message,
                userId != null && !userId.isBlank() ? userId : "debug-user");
    }
}
