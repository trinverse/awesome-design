package com.bank.batchorchestrator.controller;

import com.bank.batchorchestrator.model.JobExecutionDto;
import com.bank.batchorchestrator.service.OrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/executions")
@RequiredArgsConstructor
@Tag(name = "Execution Management", description = "APIs for managing job executions")
public class ExecutionController {
    private final OrchestratorService orchestratorService;
    
    @GetMapping("/{executionId}")
    @Operation(summary = "Get execution status by ID")
    public ResponseEntity<JobExecutionDto> getExecutionStatus(@PathVariable String executionId) {
        JobExecutionDto execution = orchestratorService.getExecutionStatus(executionId);
        return ResponseEntity.ok(execution);
    }
    
    @PostMapping("/{executionId}/cancel")
    @Operation(summary = "Cancel a running job execution")
    public ResponseEntity<JobExecutionDto> cancelExecution(@PathVariable String executionId) {
        JobExecutionDto execution = orchestratorService.cancelJob(executionId);
        return ResponseEntity.ok(execution);
    }
    
    @PostMapping("/{executionId}/retry")
    @Operation(summary = "Retry a failed job execution")
    public ResponseEntity<JobExecutionDto> retryExecution(@PathVariable String executionId) {
        JobExecutionDto execution = orchestratorService.retryJob(executionId);
        return ResponseEntity.ok(execution);
    }
    
    @GetMapping("/running")
    @Operation(summary = "Get all running job executions")
    public ResponseEntity<List<JobExecutionDto>> getRunningExecutions() {
        List<JobExecutionDto> executions = orchestratorService.getRunningJobs();
        return ResponseEntity.ok(executions);
    }
}