package com.bank.batchorchestrator.service;

import com.bank.batchorchestrator.entity.Job;
import com.bank.batchorchestrator.entity.JobExecution;
import com.bank.batchorchestrator.entity.JobStatus;
import com.bank.batchorchestrator.exception.JobExecutionException;
import com.bank.batchorchestrator.exception.JobNotFoundException;
import com.bank.batchorchestrator.model.JobExecutionDto;
import com.bank.batchorchestrator.repository.JobExecutionRepository;
import com.bank.batchorchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorService {
    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobExecutionEngine executionEngine;
    private final MonitoringService monitoringService;
    
    private final Map<String, CompletableFuture<JobExecutionDto>> runningJobs = new ConcurrentHashMap<>();
    
    @Transactional
    public JobExecutionDto submitJob(String jobName, String triggeredBy, Map<String, String> parameters) {
        log.info("Submitting job: {} triggered by: {}", jobName, triggeredBy);
        
        Job job = jobRepository.findByJobNameWithDependencies(jobName)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobName));
        
        if (!job.getActive()) {
            throw new JobExecutionException("Job is not active: " + jobName);
        }
        
        // Check dependencies
        if (!checkDependencies(job)) {
            log.warn("Dependencies not met for job: {}", jobName);
            return createPendingExecution(job, triggeredBy, parameters, JobStatus.WAITING_DEPENDENCY);
        }
        
        // Create execution record
        JobExecution execution = createExecution(job, triggeredBy, parameters);
        
        // Submit for execution
        CompletableFuture<JobExecutionDto> future = executionEngine.executeJob(execution);
        runningJobs.put(execution.getExecutionId(), future);
        
        // Handle completion
        future.whenComplete((result, error) -> {
            runningJobs.remove(execution.getExecutionId());
            if (error != null) {
                handleJobFailure(execution, error);
            } else {
                handleJobSuccess(execution, result);
            }
        });
        
        return convertToDto(execution);
    }
    
    @Transactional
    public JobExecutionDto cancelJob(String executionId) {
        log.info("Cancelling job execution: {}", executionId);
        
        JobExecution execution = jobExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new JobExecutionException("Execution not found: " + executionId));
        
        if (execution.getStatus() != JobStatus.RUNNING && execution.getStatus() != JobStatus.QUEUED) {
            throw new JobExecutionException("Cannot cancel job in status: " + execution.getStatus());
        }
        
        // Cancel the running future if exists
        CompletableFuture<JobExecutionDto> future = runningJobs.get(executionId);
        if (future != null) {
            future.cancel(true);
        }
        
        execution.setStatus(JobStatus.CANCELLED);
        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMillis(
                java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
        );
        
        JobExecution savedExecution = jobExecutionRepository.save(execution);
        
        monitoringService.recordJobCancellation(savedExecution);
        
        return convertToDto(savedExecution);
    }
    
    @Transactional
    public JobExecutionDto retryJob(String executionId) {
        log.info("Retrying job execution: {}", executionId);
        
        JobExecution originalExecution = jobExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new JobExecutionException("Execution not found: " + executionId));
        
        if (originalExecution.getStatus() != JobStatus.FAILED && 
            originalExecution.getStatus() != JobStatus.TIMEOUT) {
            throw new JobExecutionException("Can only retry failed or timed-out jobs");
        }
        
        Job job = originalExecution.getJob();
        
        if (originalExecution.getRetryCount() >= job.getMaxRetries()) {
            throw new JobExecutionException("Maximum retry attempts reached");
        }
        
        // Create new execution for retry
        JobExecution retryExecution = JobExecution.builder()
                .executionId(UUID.randomUUID().toString())
                .job(job)
                .status(JobStatus.RETRYING)
                .triggeredBy("RETRY")
                .retryCount(originalExecution.getRetryCount() + 1)
                .executionParameters(originalExecution.getExecutionParameters())
                .startTime(LocalDateTime.now())
                .build();
        
        JobExecution savedExecution = jobExecutionRepository.save(retryExecution);
        
        // Submit for execution
        CompletableFuture<JobExecutionDto> future = executionEngine.executeJob(savedExecution);
        runningJobs.put(savedExecution.getExecutionId(), future);
        
        return convertToDto(savedExecution);
    }
    
    @Transactional(readOnly = true)
    public JobExecutionDto getExecutionStatus(String executionId) {
        JobExecution execution = jobExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new JobExecutionException("Execution not found: " + executionId));
        return convertToDto(execution);
    }
    
    @Transactional(readOnly = true)
    public List<JobExecutionDto> getJobExecutions(Long jobId, int limit) {
        return jobExecutionRepository.findByJobIdOrderByStartTimeDesc(jobId).stream()
                .limit(limit)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<JobExecutionDto> getRunningJobs() {
        List<JobStatus> runningStatuses = Arrays.asList(JobStatus.RUNNING, JobStatus.QUEUED);
        return jobExecutionRepository.findByStatusIn(runningStatuses).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void checkAndHandleTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<JobExecution> timedOutExecutions = jobExecutionRepository.findTimedOutExecutions(
                now.minusMinutes(120) // Default timeout, should be per-job
        );
        
        for (JobExecution execution : timedOutExecutions) {
            log.warn("Job execution timed out: {}", execution.getExecutionId());
            
            execution.setStatus(JobStatus.TIMEOUT);
            execution.setEndTime(now);
            execution.setErrorMessage("Job execution timed out");
            jobExecutionRepository.save(execution);
            
            monitoringService.recordJobTimeout(execution);
            
            // Cancel the running future if exists
            CompletableFuture<JobExecutionDto> future = runningJobs.get(execution.getExecutionId());
            if (future != null) {
                future.cancel(true);
                runningJobs.remove(execution.getExecutionId());
            }
        }
    }
    
    private boolean checkDependencies(Job job) {
        if (job.getDependencies().isEmpty()) {
            return true;
        }
        
        for (Job dependency : job.getDependencies()) {
            // Check if dependency has a successful execution today
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
            List<JobExecution> recentExecutions = jobExecutionRepository.findRecentExecutions(
                    dependency.getId(), todayStart
            );
            
            boolean hasSuccessfulExecution = recentExecutions.stream()
                    .anyMatch(exec -> exec.getStatus() == JobStatus.SUCCESS);
            
            if (!hasSuccessfulExecution) {
                log.debug("Dependency not met: {} for job: {}", dependency.getJobName(), job.getJobName());
                return false;
            }
        }
        
        return true;
    }
    
    private JobExecution createExecution(Job job, String triggeredBy, Map<String, String> parameters) {
        JobExecution execution = JobExecution.builder()
                .executionId(UUID.randomUUID().toString())
                .job(job)
                .status(JobStatus.QUEUED)
                .triggeredBy(triggeredBy)
                .executionParameters(parameters != null ? parameters : new HashMap<>())
                .startTime(LocalDateTime.now())
                .build();
        
        return jobExecutionRepository.save(execution);
    }
    
    private JobExecutionDto createPendingExecution(Job job, String triggeredBy, 
                                                   Map<String, String> parameters, JobStatus status) {
        JobExecution execution = JobExecution.builder()
                .executionId(UUID.randomUUID().toString())
                .job(job)
                .status(status)
                .triggeredBy(triggeredBy)
                .executionParameters(parameters != null ? parameters : new HashMap<>())
                .build();
        
        JobExecution savedExecution = jobExecutionRepository.save(execution);
        return convertToDto(savedExecution);
    }
    
    private void handleJobSuccess(JobExecution execution, JobExecutionDto result) {
        log.info("Job execution succeeded: {}", execution.getExecutionId());
        
        execution.setStatus(JobStatus.SUCCESS);
        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMillis(
                java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
        );
        
        jobExecutionRepository.save(execution);
        monitoringService.recordJobSuccess(execution);
        
        // Trigger dependent jobs
        triggerDependentJobs(execution.getJob());
    }
    
    private void handleJobFailure(JobExecution execution, Throwable error) {
        log.error("Job execution failed: {}", execution.getExecutionId(), error);
        
        execution.setStatus(JobStatus.FAILED);
        execution.setEndTime(LocalDateTime.now());
        execution.setErrorMessage(error.getMessage());
        execution.setDurationMillis(
                java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
        );
        
        jobExecutionRepository.save(execution);
        monitoringService.recordJobFailure(execution);
    }
    
    private void triggerDependentJobs(Job completedJob) {
        List<Job> dependentJobs = jobRepository.findJobsWithDependency(completedJob);
        
        for (Job dependentJob : dependentJobs) {
            if (dependentJob.getActive() && checkDependencies(dependentJob)) {
                log.info("Triggering dependent job: {}", dependentJob.getJobName());
                submitJob(dependentJob.getJobName(), "DEPENDENCY", new HashMap<>());
            }
        }
    }
    
    private JobExecutionDto convertToDto(JobExecution execution) {
        return JobExecutionDto.builder()
                .id(execution.getId())
                .executionId(execution.getExecutionId())
                .jobId(execution.getJob().getId())
                .jobName(execution.getJob().getJobName())
                .status(execution.getStatus())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .durationMillis(execution.getDurationMillis())
                .output(execution.getOutput())
                .errorMessage(execution.getErrorMessage())
                .exitCode(execution.getExitCode())
                .retryCount(execution.getRetryCount())
                .triggeredBy(execution.getTriggeredBy())
                .executionHost(execution.getExecutionHost())
                .executionParameters(execution.getExecutionParameters())
                .build();
    }
}