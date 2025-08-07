package com.bank.batch.core.orchestrator;

import com.bank.batch.core.engine.ExecutionEngine;
import com.bank.batch.core.model.*;
import com.bank.batch.core.repository.*;
import com.bank.batch.core.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core orchestrator service responsible for coordinating job execution.
 * Handles job scheduling, dependency resolution, and execution management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobOrchestrator {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobScheduleRepository jobScheduleRepository;
    private final JobExecutionHistoryRepository executionHistoryRepository;
    private final JobDependencyRepository dependencyRepository;
    private final DependencyResolver dependencyResolver;
    private final ExecutionEngine executionEngine;
    private final ResourceManager resourceManager;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executorService;

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();
    private final Set<Long> runningJobs = ConcurrentHashMap.newKeySet();
    private Counter jobSubmittedCounter;
    private Counter jobCompletedCounter;
    private Counter jobFailedCounter;
    private Timer jobExecutionTimer;

    @PostConstruct
    public void init() {
        this.jobSubmittedCounter = Counter.builder("batch.jobs.submitted")
                .description("Number of jobs submitted for execution")
                .register(meterRegistry);
        
        this.jobCompletedCounter = Counter.builder("batch.jobs.completed")
                .description("Number of jobs completed successfully")
                .register(meterRegistry);
        
        this.jobFailedCounter = Counter.builder("batch.jobs.failed")
                .description("Number of jobs that failed")
                .register(meterRegistry);
        
        this.jobExecutionTimer = Timer.builder("batch.jobs.execution.time")
                .description("Job execution time")
                .register(meterRegistry);
        
        log.info("Job Orchestrator initialized successfully");
    }

    /**
     * Main scheduling loop that polls for jobs ready to execute.
     */
    @Scheduled(fixedDelayString = "${batch.orchestrator.scheduler.poll-interval-seconds}000")
    public void scheduleJobs() {
        try {
            log.debug("Starting job scheduling cycle");
            
            // Get jobs ready for execution
            List<JobSchedule> readySchedules = jobScheduleRepository.findJobsReadyToExecute(
                LocalDateTime.now(),
                100 // batch size
            );
            
            log.info("Found {} jobs ready for execution", readySchedules.size());
            
            for (JobSchedule schedule : readySchedules) {
                try {
                    submitJobForExecution(schedule);
                } catch (Exception e) {
                    log.error("Failed to submit job {} for execution", 
                        schedule.getJob().getJobName(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in job scheduling cycle", e);
        }
    }

    /**
     * Submits a job for execution after checking dependencies and resources.
     */
    @Transactional
    public CompletableFuture<JobExecutionHistory> submitJobForExecution(JobSchedule schedule) {
        JobDefinition job = schedule.getJob();
        
        log.info("Submitting job {} for execution", job.getJobName());
        
        // Check if job is already running
        if (runningJobs.contains(job.getJobId())) {
            log.warn("Job {} is already running, skipping submission", job.getJobName());
            return CompletableFuture.completedFuture(null);
        }
        
        // Check dependencies
        if (!dependencyResolver.checkDependencies(job.getJobId())) {
            log.info("Dependencies not met for job {}", job.getJobName());
            return CompletableFuture.completedFuture(null);
        }
        
        // Check resource availability
        if (!resourceManager.checkResourceAvailability(job)) {
            log.info("Resources not available for job {}", job.getJobName());
            return CompletableFuture.completedFuture(null);
        }
        
        // Create execution history record
        JobExecutionHistory execution = createExecutionHistory(job, schedule, "SCHEDULED");
        
        // Submit job for execution
        CompletableFuture<JobExecutionHistory> future = CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                runningJobs.add(job.getJobId());
                jobSubmittedCounter.increment();
                
                // Update execution status to RUNNING
                execution.setStatus(ExecutionStatus.RUNNING);
                execution.setStartTime(LocalDateTime.now());
                executionHistoryRepository.save(execution);
                
                // Execute the job
                JobExecutionResult result = executionEngine.executeJob(job, execution);
                
                // Update execution history with result
                updateExecutionHistory(execution, result);
                
                // Handle post-execution actions
                handlePostExecution(job, execution, result);
                
                if (result.isSuccess()) {
                    jobCompletedCounter.increment();
                } else {
                    jobFailedCounter.increment();
                }
                
                return execution;
                
            } catch (Exception e) {
                log.error("Error executing job {}", job.getJobName(), e);
                execution.setStatus(ExecutionStatus.FAILURE);
                execution.setErrorMessage(e.getMessage());
                execution.setEndTime(LocalDateTime.now());
                executionHistoryRepository.save(execution);
                jobFailedCounter.increment();
                
                // Send failure alert
                alertService.sendAlert(job, execution, AlertType.FAILURE);
                
                return execution;
                
            } finally {
                runningJobs.remove(job.getJobId());
                sample.stop(jobExecutionTimer);
                
                // Release resources
                resourceManager.releaseResources(job);
                
                // Update next run time for schedule
                updateNextRunTime(schedule);
            }
        }, executorService);
        
        return future;
    }

    /**
     * Manually triggers a job execution.
     */
    @Transactional
    public JobExecutionHistory triggerJob(Long jobId, String triggeredBy, Map<String, String> parameters) {
        log.info("Manual trigger requested for job {} by {}", jobId, triggeredBy);
        
        JobDefinition job = jobDefinitionRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        
        if (!job.isActive()) {
            throw new JobInactiveException("Job is not active: " + job.getJobName());
        }
        
        // Create execution history with MANUAL trigger type
        JobExecutionHistory execution = new JobExecutionHistory();
        execution.setJob(job);
        execution.setTriggerType(TriggerType.MANUAL);
        execution.setTriggeredBy(triggeredBy);
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setCreatedDate(LocalDateTime.now());
        
        if (parameters != null && !parameters.isEmpty()) {
            execution.setInputParameters(convertToJson(parameters));
        }
        
        execution = executionHistoryRepository.save(execution);
        
        // Submit for execution
        submitJobForExecution(null, execution);
        
        return execution;
    }

    /**
     * Cancels a running job execution.
     */
    @Transactional
    public boolean cancelJob(Long executionId, String cancelledBy) {
        log.info("Cancel request for execution {} by {}", executionId, cancelledBy);
        
        JobExecutionHistory execution = executionHistoryRepository.findById(executionId)
            .orElseThrow(() -> new ExecutionNotFoundException("Execution not found: " + executionId));
        
        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            log.warn("Cannot cancel execution {} with status {}", executionId, execution.getStatus());
            return false;
        }
        
        // Request cancellation from execution engine
        boolean cancelled = executionEngine.cancelExecution(executionId);
        
        if (cancelled) {
            execution.setStatus(ExecutionStatus.CANCELLED);
            execution.setEndTime(LocalDateTime.now());
            execution.setErrorMessage("Cancelled by " + cancelledBy);
            executionHistoryRepository.save(execution);
            
            // Send cancellation alert
            alertService.sendAlert(execution.getJob(), execution, AlertType.CANCELLED);
        }
        
        return cancelled;
    }

    /**
     * Retries a failed job execution.
     */
    @Transactional
    public JobExecutionHistory retryJob(Long executionId, String retriedBy) {
        log.info("Retry request for execution {} by {}", executionId, retriedBy);
        
        JobExecutionHistory originalExecution = executionHistoryRepository.findById(executionId)
            .orElseThrow(() -> new ExecutionNotFoundException("Execution not found: " + executionId));
        
        if (originalExecution.getStatus() != ExecutionStatus.FAILURE) {
            throw new InvalidRetryException("Can only retry failed executions");
        }
        
        JobDefinition job = originalExecution.getJob();
        
        // Check retry count
        int retryCount = originalExecution.getRetryCount() + 1;
        if (retryCount > job.getMaxRetryCount()) {
            throw new MaxRetryExceededException("Maximum retry count exceeded for job: " + job.getJobName());
        }
        
        // Create new execution for retry
        JobExecutionHistory retryExecution = new JobExecutionHistory();
        retryExecution.setJob(job);
        retryExecution.setTriggerType(TriggerType.RETRY);
        retryExecution.setTriggeredBy(retriedBy);
        retryExecution.setStatus(ExecutionStatus.PENDING);
        retryExecution.setRetryCount(retryCount);
        retryExecution.setInputParameters(originalExecution.getInputParameters());
        retryExecution.setCreatedDate(LocalDateTime.now());
        
        retryExecution = executionHistoryRepository.save(retryExecution);
        
        // Submit for execution
        submitJobForExecution(null, retryExecution);
        
        return retryExecution;
    }

    /**
     * Gets the current status of all running jobs.
     */
    public List<JobExecutionStatus> getRunningJobs() {
        return executionHistoryRepository.findByStatus(ExecutionStatus.RUNNING)
            .stream()
            .map(this::mapToExecutionStatus)
            .collect(Collectors.toList());
    }

    /**
     * Gets job execution history with filtering.
     */
    public Page<JobExecutionHistory> getExecutionHistory(
            Long jobId, 
            LocalDateTime startDate, 
            LocalDateTime endDate,
            ExecutionStatus status,
            Pageable pageable) {
        
        return executionHistoryRepository.findByFilters(jobId, startDate, endDate, status, pageable);
    }

    /**
     * Pauses a scheduled job.
     */
    @Transactional
    public void pauseJob(Long jobId) {
        log.info("Pausing job {}", jobId);
        
        JobDefinition job = jobDefinitionRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        
        // Deactivate all schedules
        jobScheduleRepository.deactivateSchedulesForJob(jobId);
        
        // Cancel any scheduled futures
        ScheduledFuture<?> future = scheduledJobs.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
        
        log.info("Job {} paused successfully", job.getJobName());
    }

    /**
     * Resumes a paused job.
     */
    @Transactional
    public void resumeJob(Long jobId) {
        log.info("Resuming job {}", jobId);
        
        JobDefinition job = jobDefinitionRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        
        // Reactivate schedules
        jobScheduleRepository.activateSchedulesForJob(jobId);
        
        log.info("Job {} resumed successfully", job.getJobName());
    }

    // Private helper methods

    private JobExecutionHistory createExecutionHistory(JobDefinition job, JobSchedule schedule, String triggerType) {
        JobExecutionHistory execution = new JobExecutionHistory();
        execution.setJob(job);
        execution.setSchedule(schedule);
        execution.setTriggerType(TriggerType.valueOf(triggerType));
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setCreatedDate(LocalDateTime.now());
        
        // Copy job parameters as input parameters
        Map<String, String> parameters = jobParameterRepository.findByJobId(job.getJobId())
            .stream()
            .collect(Collectors.toMap(
                JobParameter::getParameterName,
                JobParameter::getParameterValue
            ));
        
        if (!parameters.isEmpty()) {
            execution.setInputParameters(convertToJson(parameters));
        }
        
        return executionHistoryRepository.save(execution);
    }

    private void updateExecutionHistory(JobExecutionHistory execution, JobExecutionResult result) {
        execution.setStatus(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE);
        execution.setEndTime(LocalDateTime.now());
        execution.setErrorMessage(result.getErrorMessage());
        execution.setWarningMessage(result.getWarningMessage());
        execution.setOutputParameters(result.getOutputParameters());
        execution.setLogFilePath(result.getLogFilePath());
        
        executionHistoryRepository.save(execution);
    }

    private void handlePostExecution(JobDefinition job, JobExecutionHistory execution, JobExecutionResult result) {
        // Send alerts based on configuration
        if (result.isSuccess() && job.isAlertOnSuccess()) {
            alertService.sendAlert(job, execution, AlertType.SUCCESS);
        } else if (!result.isSuccess() && job.isAlertOnFailure()) {
            alertService.sendAlert(job, execution, AlertType.FAILURE);
        }
        
        // Trigger dependent jobs if successful
        if (result.isSuccess()) {
            triggerDependentJobs(job.getJobId());
        }
        
        // Check for automatic retry on failure
        if (!result.isSuccess() && execution.getRetryCount() < job.getMaxRetryCount()) {
            scheduleRetry(job, execution);
        }
    }

    private void triggerDependentJobs(Long jobId) {
        List<JobDependency> dependencies = dependencyRepository.findDependentJobs(jobId);
        
        for (JobDependency dependency : dependencies) {
            if (dependency.isActive() && dependencyResolver.checkDependencies(dependency.getJobId())) {
                log.info("Triggering dependent job {} after completion of {}", 
                    dependency.getJob().getJobName(), jobId);
                
                triggerJob(dependency.getJobId(), "DEPENDENCY", null);
            }
        }
    }

    private void scheduleRetry(JobDefinition job, JobExecutionHistory execution) {
        int retryDelay = job.getRetryIntervalSeconds();
        
        log.info("Scheduling retry for job {} in {} seconds", job.getJobName(), retryDelay);
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            retryJob(execution.getExecutionId(), "SYSTEM");
        }, retryDelay, TimeUnit.SECONDS);
    }

    private void updateNextRunTime(JobSchedule schedule) {
        if (schedule != null && schedule.getScheduleType() != ScheduleType.ONE_TIME) {
            LocalDateTime nextRunTime = scheduleCalculator.calculateNextRunTime(schedule);
            schedule.setNextRunTime(nextRunTime);
            schedule.setLastRunTime(LocalDateTime.now());
            jobScheduleRepository.save(schedule);
        }
    }

    private JobExecutionStatus mapToExecutionStatus(JobExecutionHistory execution) {
        return JobExecutionStatus.builder()
            .executionId(execution.getExecutionId())
            .jobName(execution.getJob().getJobName())
            .status(execution.getStatus())
            .startTime(execution.getStartTime())
            .runningTimeSeconds(calculateRunningTime(execution.getStartTime()))
            .hostName(execution.getHostName())
            .triggeredBy(execution.getTriggeredBy())
            .build();
    }

    private long calculateRunningTime(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        return Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }

    private String convertToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to convert object to JSON", e);
            return "{}";
        }
    }
}