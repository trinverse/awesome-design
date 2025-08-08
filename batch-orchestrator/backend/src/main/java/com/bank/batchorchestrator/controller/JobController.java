package com.bank.batchorchestrator.controller;

import com.bank.batchorchestrator.model.JobDto;
import com.bank.batchorchestrator.model.JobExecutionDto;
import com.bank.batchorchestrator.service.JobConfigurationService;
import com.bank.batchorchestrator.service.OrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "APIs for managing batch jobs")
public class JobController {
    private final JobConfigurationService configurationService;
    private final OrchestratorService orchestratorService;
    
    @PostMapping
    @Operation(summary = "Create a new job")
    public ResponseEntity<JobDto> createJob(@Valid @RequestBody JobDto jobDto) {
        JobDto createdJob = configurationService.createJob(jobDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdJob);
    }
    
    @PutMapping("/{jobId}")
    @Operation(summary = "Update an existing job")
    public ResponseEntity<JobDto> updateJob(@PathVariable Long jobId, @Valid @RequestBody JobDto jobDto) {
        JobDto updatedJob = configurationService.updateJob(jobId, jobDto);
        return ResponseEntity.ok(updatedJob);
    }
    
    @GetMapping("/{jobId}")
    @Operation(summary = "Get job by ID")
    public ResponseEntity<JobDto> getJob(@PathVariable Long jobId) {
        JobDto job = configurationService.getJob(jobId);
        return ResponseEntity.ok(job);
    }
    
    @GetMapping("/name/{jobName}")
    @Operation(summary = "Get job by name")
    public ResponseEntity<JobDto> getJobByName(@PathVariable String jobName) {
        JobDto job = configurationService.getJobByName(jobName);
        return ResponseEntity.ok(job);
    }
    
    @GetMapping
    @Operation(summary = "Get all jobs")
    public ResponseEntity<List<JobDto>> getAllJobs(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<JobDto> jobs = activeOnly ? configurationService.getActiveJobs() : configurationService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }
    
    @DeleteMapping("/{jobId}")
    @Operation(summary = "Delete a job")
    public ResponseEntity<Void> deleteJob(@PathVariable Long jobId) {
        configurationService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{jobId}/toggle")
    @Operation(summary = "Toggle job active status")
    public ResponseEntity<Void> toggleJobStatus(@PathVariable Long jobId) {
        configurationService.toggleJobStatus(jobId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{jobId}/dependencies/{dependencyId}")
    @Operation(summary = "Add dependency to job")
    public ResponseEntity<Void> addDependency(@PathVariable Long jobId, @PathVariable Long dependencyId) {
        configurationService.addDependency(jobId, dependencyId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{jobId}/dependencies/{dependencyId}")
    @Operation(summary = "Remove dependency from job")
    public ResponseEntity<Void> removeDependency(@PathVariable Long jobId, @PathVariable Long dependencyId) {
        configurationService.removeDependency(jobId, dependencyId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{jobName}/execute")
    @Operation(summary = "Execute a job manually")
    public ResponseEntity<JobExecutionDto> executeJob(
            @PathVariable String jobName,
            @RequestBody(required = false) Map<String, String> parameters) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        JobExecutionDto execution = orchestratorService.submitJob(jobName, "MANUAL", parameters);
        return ResponseEntity.ok(execution);
    }
    
    @GetMapping("/{jobId}/executions")
    @Operation(summary = "Get job execution history")
    public ResponseEntity<List<JobExecutionDto>> getJobExecutions(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "10") int limit) {
        List<JobExecutionDto> executions = orchestratorService.getJobExecutions(jobId, limit);
        return ResponseEntity.ok(executions);
    }
}