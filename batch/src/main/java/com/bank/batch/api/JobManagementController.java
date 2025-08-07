package com.bank.batch.api;

import com.bank.batch.core.orchestrator.JobOrchestrator;
import com.bank.batch.core.model.*;
import com.bank.batch.core.service.*;
import com.bank.batch.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for job management operations.
 * Provides endpoints for job configuration, execution, and monitoring.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job Management", description = "Job configuration and execution management")
@SecurityRequirement(name = "bearerAuth")
@Validated
@Slf4j
@RequiredArgsConstructor
public class JobManagementController {

    private final JobOrchestrator orchestrator;
    private final JobDefinitionService jobDefinitionService;
    private final JobScheduleService jobScheduleService;
    private final JobExecutionService jobExecutionService;
    private final JobDependencyService jobDependencyService;
    private final JobGroupService jobGroupService;

    // ==================== Job Definition Endpoints ====================

    @GetMapping
    @Operation(summary = "Get all job definitions", description = "Retrieves a paginated list of all job definitions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved job definitions"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<Page<JobDefinitionDTO>> getAllJobs(
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        
        log.debug("Fetching job definitions with filters - group: {}, type: {}, active: {}", 
            jobGroup, jobType, isActive);
        
        Page<JobDefinitionDTO> jobs = jobDefinitionService.findJobs(jobGroup, jobType, isActive, pageable);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get job definition by ID", description = "Retrieves a specific job definition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job found"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<JobDefinitionDTO> getJob(@PathVariable Long jobId) {
        log.debug("Fetching job definition for ID: {}", jobId);
        
        JobDefinitionDTO job = jobDefinitionService.findById(jobId);
        return ResponseEntity.ok(job);
    }

    @PostMapping
    @Operation(summary = "Create new job definition", description = "Creates a new job definition")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Job created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Job already exists")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobDefinitionDTO> createJob(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader("X-User") String createdBy) {
        
        log.info("Creating new job: {} by user: {}", request.getJobName(), createdBy);
        
        JobDefinitionDTO created = jobDefinitionService.createJob(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{jobId}")
    @Operation(summary = "Update job definition", description = "Updates an existing job definition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobDefinitionDTO> updateJob(
            @PathVariable Long jobId,
            @Valid @RequestBody UpdateJobRequest request,
            @RequestHeader("X-User") String modifiedBy) {
        
        log.info("Updating job ID: {} by user: {}", jobId, modifiedBy);
        
        JobDefinitionDTO updated = jobDefinitionService.updateJob(jobId, request, modifiedBy);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "Delete job definition", description = "Deletes a job definition")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Job deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "409", description = "Job has active dependencies")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteJob(
            @PathVariable Long jobId,
            @RequestHeader("X-User") String deletedBy) {
        
        log.info("Deleting job ID: {} by user: {}", jobId, deletedBy);
        
        jobDefinitionService.deleteJob(jobId, deletedBy);
        return ResponseEntity.noContent().build();
    }

    // ==================== Job Execution Endpoints ====================

    @PostMapping("/{jobId}/execute")
    @Operation(summary = "Execute job manually", description = "Triggers manual execution of a job")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job execution started"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "409", description = "Job is already running")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<JobExecutionDTO> executeJob(
            @PathVariable Long jobId,
            @RequestBody(required = false) Map<String, String> parameters,
            @RequestHeader("X-User") String triggeredBy) {
        
        log.info("Manual execution of job ID: {} triggered by: {}", jobId, triggeredBy);
        
        JobExecutionHistory execution = orchestrator.triggerJob(jobId, triggeredBy, parameters);
        JobExecutionDTO dto = mapToExecutionDTO(execution);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @PostMapping("/executions/{executionId}/cancel")
    @Operation(summary = "Cancel job execution", description = "Cancels a running job execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cancellation requested"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "409", description = "Job is not running")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<String> cancelExecution(
            @PathVariable Long executionId,
            @RequestHeader("X-User") String cancelledBy) {
        
        log.info("Cancelling execution ID: {} by user: {}", executionId, cancelledBy);
        
        boolean cancelled = orchestrator.cancelJob(executionId, cancelledBy);
        
        if (cancelled) {
            return ResponseEntity.ok("Job execution cancelled successfully");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Failed to cancel job execution");
        }
    }

    @PostMapping("/executions/{executionId}/retry")
    @Operation(summary = "Retry failed job", description = "Retries a failed job execution")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Retry started"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "409", description = "Job cannot be retried")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<JobExecutionDTO> retryExecution(
            @PathVariable Long executionId,
            @RequestHeader("X-User") String retriedBy) {
        
        log.info("Retrying execution ID: {} by user: {}", executionId, retriedBy);
        
        JobExecutionHistory retryExecution = orchestrator.retryJob(executionId, retriedBy);
        JobExecutionDTO dto = mapToExecutionDTO(retryExecution);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    // ==================== Job Monitoring Endpoints ====================

    @GetMapping("/running")
    @Operation(summary = "Get running jobs", description = "Retrieves list of currently running jobs")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved running jobs")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<JobExecutionStatus>> getRunningJobs() {
        log.debug("Fetching running jobs");
        
        List<JobExecutionStatus> runningJobs = orchestrator.getRunningJobs();
        return ResponseEntity.ok(runningJobs);
    }

    @GetMapping("/executions")
    @Operation(summary = "Get execution history", description = "Retrieves job execution history with filtering")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved execution history")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<Page<JobExecutionDTO>> getExecutionHistory(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        log.debug("Fetching execution history with filters - jobId: {}, startDate: {}, endDate: {}, status: {}", 
            jobId, startDate, endDate, status);
        
        Page<JobExecutionDTO> history = jobExecutionService.getExecutionHistory(
            jobId, startDate, endDate, status, pageable);
        
        return ResponseEntity.ok(history);
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "Get execution details", description = "Retrieves detailed information about a specific execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution found"),
        @ApiResponse(responseCode = "404", description = "Execution not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<JobExecutionDetailDTO> getExecutionDetails(@PathVariable Long executionId) {
        log.debug("Fetching execution details for ID: {}", executionId);
        
        JobExecutionDetailDTO details = jobExecutionService.getExecutionDetails(executionId);
        return ResponseEntity.ok(details);
    }

    // ==================== Job Schedule Endpoints ====================

    @GetMapping("/{jobId}/schedules")
    @Operation(summary = "Get job schedules", description = "Retrieves schedules for a specific job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved schedules"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<JobScheduleDTO>> getJobSchedules(@PathVariable Long jobId) {
        log.debug("Fetching schedules for job ID: {}", jobId);
        
        List<JobScheduleDTO> schedules = jobScheduleService.getSchedulesForJob(jobId);
        return ResponseEntity.ok(schedules);
    }

    @PostMapping("/{jobId}/schedules")
    @Operation(summary = "Create job schedule", description = "Creates a new schedule for a job")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schedule created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid schedule configuration"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobScheduleDTO> createSchedule(
            @PathVariable Long jobId,
            @Valid @RequestBody CreateScheduleRequest request,
            @RequestHeader("X-User") String createdBy) {
        
        log.info("Creating schedule for job ID: {} by user: {}", jobId, createdBy);
        
        JobScheduleDTO created = jobScheduleService.createSchedule(jobId, request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/schedules/{scheduleId}")
    @Operation(summary = "Update job schedule", description = "Updates an existing job schedule")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schedule updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid schedule configuration"),
        @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobScheduleDTO> updateSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateScheduleRequest request,
            @RequestHeader("X-User") String modifiedBy) {
        
        log.info("Updating schedule ID: {} by user: {}", scheduleId, modifiedBy);
        
        JobScheduleDTO updated = jobScheduleService.updateSchedule(scheduleId, request, modifiedBy);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @Operation(summary = "Delete job schedule", description = "Deletes a job schedule")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Schedule deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long scheduleId,
            @RequestHeader("X-User") String deletedBy) {
        
        log.info("Deleting schedule ID: {} by user: {}", scheduleId, deletedBy);
        
        jobScheduleService.deleteSchedule(scheduleId, deletedBy);
        return ResponseEntity.noContent().build();
    }

    // ==================== Job Control Endpoints ====================

    @PostMapping("/{jobId}/pause")
    @Operation(summary = "Pause job", description = "Pauses a scheduled job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job paused successfully"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<String> pauseJob(
            @PathVariable Long jobId,
            @RequestHeader("X-User") String pausedBy) {
        
        log.info("Pausing job ID: {} by user: {}", jobId, pausedBy);
        
        orchestrator.pauseJob(jobId);
        return ResponseEntity.ok("Job paused successfully");
    }

    @PostMapping("/{jobId}/resume")
    @Operation(summary = "Resume job", description = "Resumes a paused job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job resumed successfully"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<String> resumeJob(
            @PathVariable Long jobId,
            @RequestHeader("X-User") String resumedBy) {
        
        log.info("Resuming job ID: {} by user: {}", jobId, resumedBy);
        
        orchestrator.resumeJob(jobId);
        return ResponseEntity.ok("Job resumed successfully");
    }

    // ==================== Job Dependencies Endpoints ====================

    @GetMapping("/{jobId}/dependencies")
    @Operation(summary = "Get job dependencies", description = "Retrieves dependencies for a job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved dependencies"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<JobDependencyDTO>> getJobDependencies(@PathVariable Long jobId) {
        log.debug("Fetching dependencies for job ID: {}", jobId);
        
        List<JobDependencyDTO> dependencies = jobDependencyService.getDependenciesForJob(jobId);
        return ResponseEntity.ok(dependencies);
    }

    @PostMapping("/{jobId}/dependencies")
    @Operation(summary = "Add job dependency", description = "Adds a dependency to a job")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Dependency added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid dependency"),
        @ApiResponse(responseCode = "409", description = "Circular dependency detected")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobDependencyDTO> addDependency(
            @PathVariable Long jobId,
            @Valid @RequestBody CreateDependencyRequest request,
            @RequestHeader("X-User") String createdBy) {
        
        log.info("Adding dependency to job ID: {} by user: {}", jobId, createdBy);
        
        JobDependencyDTO created = jobDependencyService.createDependency(jobId, request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    @Operation(summary = "Remove job dependency", description = "Removes a job dependency")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Dependency removed successfully"),
        @ApiResponse(responseCode = "404", description = "Dependency not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long dependencyId,
            @RequestHeader("X-User") String deletedBy) {
        
        log.info("Removing dependency ID: {} by user: {}", dependencyId, deletedBy);
        
        jobDependencyService.deleteDependency(dependencyId, deletedBy);
        return ResponseEntity.noContent().build();
    }

    // ==================== Helper Methods ====================

    private JobExecutionDTO mapToExecutionDTO(JobExecutionHistory execution) {
        return JobExecutionDTO.builder()
            .executionId(execution.getExecutionId())
            .jobId(execution.getJob().getJobId())
            .jobName(execution.getJob().getJobName())
            .status(execution.getStatus().toString())
            .triggerType(execution.getTriggerType().toString())
            .triggeredBy(execution.getTriggeredBy())
            .startTime(execution.getStartTime())
            .endTime(execution.getEndTime())
            .durationSeconds(execution.getDurationSeconds())
            .build();
    }
}