package com.bank.batch.core.engine;

import com.bank.batch.core.executor.*;
import com.bank.batch.core.model.*;
import com.bank.batch.core.repository.*;
import com.bank.batch.symphony.SymphonyGridClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Core execution engine responsible for running jobs.
 * Integrates with IBM Symphony Grid for distributed execution.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionEngine {

    private final JobExecutorFactory executorFactory;
    private final SymphonyGridClient symphonyClient;
    private final JobExecutionStepRepository stepRepository;
    private final ExecutorService localExecutorService;
    private final Map<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();
    
    @Value("${batch.orchestrator.symphony.enabled}")
    private boolean symphonyEnabled;
    
    @Value("${batch.orchestrator.executor.timeout-minutes:60}")
    private int defaultTimeoutMinutes;

    /**
     * Executes a job based on its type and configuration.
     */
    public JobExecutionResult executeJob(JobDefinition job, JobExecutionHistory execution) {
        log.info("Starting execution of job: {} (ID: {})", job.getJobName(), execution.getExecutionId());
        
        JobExecutionResult result = new JobExecutionResult();
        result.setExecutionId(execution.getExecutionId());
        result.setJobId(job.getJobId());
        
        try {
            // Set execution context
            ExecutionContext context = buildExecutionContext(job, execution);
            
            // Get appropriate executor
            JobExecutor executor = executorFactory.getExecutor(job.getJobType());
            
            // Determine execution mode
            if (shouldUseGrid(job)) {
                result = executeOnGrid(job, execution, context, executor);
            } else {
                result = executeLocally(job, execution, context, executor);
            }
            
            log.info("Job {} completed with status: {}", job.getJobName(), 
                result.isSuccess() ? "SUCCESS" : "FAILURE");
            
        } catch (TimeoutException e) {
            log.error("Job {} timed out after {} minutes", job.getJobName(), job.getTimeoutMinutes());
            result.setSuccess(false);
            result.setErrorMessage("Job execution timed out");
            result.setTimedOut(true);
            
        } catch (InterruptedException e) {
            log.warn("Job {} was interrupted", job.getJobName());
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setErrorMessage("Job execution was interrupted");
            result.setCancelled(true);
            
        } catch (Exception e) {
            log.error("Error executing job {}", job.getJobName(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTrace(e));
        }
        
        return result;
    }

    /**
     * Executes a job locally on the orchestrator node.
     */
    private JobExecutionResult executeLocally(
            JobDefinition job, 
            JobExecutionHistory execution,
            ExecutionContext context,
            JobExecutor executor) throws Exception {
        
        log.debug("Executing job {} locally", job.getJobName());
        
        // Create callable task
        Callable<JobExecutionResult> task = () -> {
            try {
                // Update host information
                execution.setHostName(getHostName());
                execution.setProcessId(getProcessId());
                execution.setThreadId(Thread.currentThread().getName());
                
                // Execute with steps tracking
                return executeWithSteps(job, execution, context, executor);
                
            } catch (Exception e) {
                log.error("Error in local execution of job {}", job.getJobName(), e);
                throw e;
            }
        };
        
        // Submit task with timeout
        Future<JobExecutionResult> future = localExecutorService.submit(task);
        runningTasks.put(execution.getExecutionId(), future);
        
        try {
            int timeout = job.getTimeoutMinutes() > 0 ? job.getTimeoutMinutes() : defaultTimeoutMinutes;
            return future.get(timeout, TimeUnit.MINUTES);
            
        } finally {
            runningTasks.remove(execution.getExecutionId());
        }
    }

    /**
     * Executes a job on Symphony Grid for distributed processing.
     */
    @CircuitBreaker(name = "symphony-api")
    @Retry(name = "symphony-api")
    private JobExecutionResult executeOnGrid(
            JobDefinition job,
            JobExecutionHistory execution,
            ExecutionContext context,
            JobExecutor executor) throws Exception {
        
        log.debug("Executing job {} on Symphony Grid", job.getJobName());
        
        // Prepare Symphony job request
        SymphonyJobRequest request = SymphonyJobRequest.builder()
            .jobId(job.getJobId())
            .jobName(job.getJobName())
            .executionId(execution.getExecutionId())
            .jobType(job.getJobType().toString())
            .priority(job.getPriority())
            .timeoutMinutes(job.getTimeoutMinutes())
            .resourceRequirements(buildResourceRequirements(job))
            .executionContext(context)
            .build();
        
        // Submit to Symphony Grid
        SymphonyJobResponse response = symphonyClient.submitJob(request);
        
        if (response == null || !response.isAccepted()) {
            log.warn("Symphony Grid rejected job {}, falling back to local execution", job.getJobName());
            return executeLocally(job, execution, context, executor);
        }
        
        // Track Symphony job
        String symphonyJobId = response.getJobId();
        execution.setHostName("symphony-grid");
        execution.setProcessId(Integer.parseInt(symphonyJobId.substring(0, Math.min(symphonyJobId.length(), 9))));
        
        // Monitor Symphony job execution
        return monitorGridExecution(symphonyJobId, job, execution);
    }

    /**
     * Executes a job with step-by-step tracking.
     */
    private JobExecutionResult executeWithSteps(
            JobDefinition job,
            JobExecutionHistory execution,
            ExecutionContext context,
            JobExecutor executor) throws Exception {
        
        JobExecutionResult result = new JobExecutionResult();
        List<JobExecutionStep> steps = new ArrayList<>();
        
        // Pre-execution step
        JobExecutionStep preStep = createStep(execution, 1, "Pre-Execution");
        preStep.setStartTime(LocalDateTime.now());
        
        try {
            executor.preExecute(context);
            preStep.setStatus(ExecutionStatus.SUCCESS);
            preStep.setOutput("Pre-execution completed successfully");
            
        } catch (Exception e) {
            preStep.setStatus(ExecutionStatus.FAILURE);
            preStep.setErrorMessage(e.getMessage());
            throw e;
            
        } finally {
            preStep.setEndTime(LocalDateTime.now());
            steps.add(stepRepository.save(preStep));
        }
        
        // Main execution step
        JobExecutionStep mainStep = createStep(execution, 2, "Main Execution");
        mainStep.setStartTime(LocalDateTime.now());
        
        try {
            result = executor.execute(context);
            mainStep.setStatus(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE);
            mainStep.setOutput(result.getOutput());
            mainStep.setErrorMessage(result.getErrorMessage());
            
        } catch (Exception e) {
            mainStep.setStatus(ExecutionStatus.FAILURE);
            mainStep.setErrorMessage(e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            
        } finally {
            mainStep.setEndTime(LocalDateTime.now());
            steps.add(stepRepository.save(mainStep));
        }
        
        // Post-execution step (only if main execution succeeded)
        if (result.isSuccess()) {
            JobExecutionStep postStep = createStep(execution, 3, "Post-Execution");
            postStep.setStartTime(LocalDateTime.now());
            
            try {
                executor.postExecute(context, result);
                postStep.setStatus(ExecutionStatus.SUCCESS);
                postStep.setOutput("Post-execution completed successfully");
                
            } catch (Exception e) {
                postStep.setStatus(ExecutionStatus.FAILURE);
                postStep.setErrorMessage(e.getMessage());
                result.setWarningMessage("Post-execution failed: " + e.getMessage());
                
            } finally {
                postStep.setEndTime(LocalDateTime.now());
                steps.add(stepRepository.save(postStep));
            }
        }
        
        result.setExecutionSteps(steps);
        return result;
    }

    /**
     * Monitors job execution on Symphony Grid.
     */
    private JobExecutionResult monitorGridExecution(
            String symphonyJobId,
            JobDefinition job,
            JobExecutionHistory execution) throws Exception {
        
        log.debug("Monitoring Symphony job {} for {}", symphonyJobId, job.getJobName());
        
        JobExecutionResult result = new JobExecutionResult();
        int pollIntervalSeconds = 10;
        int maxPollAttempts = (job.getTimeoutMinutes() * 60) / pollIntervalSeconds;
        
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            SymphonyJobStatus status = symphonyClient.getJobStatus(symphonyJobId);
            
            if (status.isCompleted()) {
                result.setSuccess(status.isSuccessful());
                result.setOutput(status.getOutput());
                result.setErrorMessage(status.getErrorMessage());
                result.setLogFilePath(status.getLogPath());
                
                // Retrieve execution metrics from Symphony
                SymphonyJobMetrics metrics = symphonyClient.getJobMetrics(symphonyJobId);
                result.setMetrics(convertMetrics(metrics));
                
                return result;
            }
            
            if (status.isFailed()) {
                result.setSuccess(false);
                result.setErrorMessage(status.getErrorMessage());
                return result;
            }
            
            // Wait before next poll
            Thread.sleep(pollIntervalSeconds * 1000);
        }
        
        // Timeout reached
        log.error("Symphony job {} timed out", symphonyJobId);
        symphonyClient.cancelJob(symphonyJobId);
        
        result.setSuccess(false);
        result.setErrorMessage("Job execution timed out on Symphony Grid");
        result.setTimedOut(true);
        
        return result;
    }

    /**
     * Cancels a running job execution.
     */
    public boolean cancelExecution(Long executionId) {
        log.info("Attempting to cancel execution {}", executionId);
        
        // Check local executions
        Future<?> future = runningTasks.get(executionId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                runningTasks.remove(executionId);
            }
            return cancelled;
        }
        
        // Check Symphony Grid executions
        if (symphonyEnabled) {
            try {
                return symphonyClient.cancelJobByExecutionId(executionId.toString());
            } catch (Exception e) {
                log.error("Failed to cancel Symphony job for execution {}", executionId, e);
            }
        }
        
        return false;
    }

    /**
     * Builds execution context for the job.
     */
    private ExecutionContext buildExecutionContext(JobDefinition job, JobExecutionHistory execution) {
        ExecutionContext context = new ExecutionContext();
        
        context.setJobId(job.getJobId());
        context.setJobName(job.getJobName());
        context.setExecutionId(execution.getExecutionId());
        context.setJobType(job.getJobType());
        
        // Set job-specific properties
        context.setScriptPath(job.getScriptPath());
        context.setClassName(job.getJobClassName());
        context.setStoredProcedure(job.getStoredProcName());
        
        // Parse and set parameters
        if (execution.getInputParameters() != null) {
            Map<String, Object> parameters = parseJsonParameters(execution.getInputParameters());
            context.setParameters(parameters);
        }
        
        // Set execution properties
        context.setRetryCount(execution.getRetryCount());
        context.setTriggeredBy(execution.getTriggeredBy());
        context.setTriggerType(execution.getTriggerType());
        
        return context;
    }

    /**
     * Determines if job should be executed on Symphony Grid.
     */
    private boolean shouldUseGrid(JobDefinition job) {
        if (!symphonyEnabled) {
            return false;
        }
        
        // Use grid for resource-intensive jobs
        if (job.getResourceRequirements() != null && !job.getResourceRequirements().isEmpty()) {
            return true;
        }
        
        // Use grid for critical jobs
        if (job.isCriticalJob()) {
            return true;
        }
        
        // Use grid based on job type
        return job.getJobType() == JobType.JAVA || job.getJobType() == JobType.PYTHON;
    }

    /**
     * Creates a job execution step record.
     */
    private JobExecutionStep createStep(JobExecutionHistory execution, int stepNumber, String stepName) {
        JobExecutionStep step = new JobExecutionStep();
        step.setExecution(execution);
        step.setStepNumber(stepNumber);
        step.setStepName(stepName);
        step.setStatus(ExecutionStatus.PENDING);
        step.setCreatedDate(LocalDateTime.now());
        return step;
    }

    /**
     * Builds resource requirements for Symphony Grid.
     */
    private Map<String, Integer> buildResourceRequirements(JobDefinition job) {
        Map<String, Integer> requirements = new HashMap<>();
        
        // Get resource requirements from database
        List<JobResourceRequirement> dbRequirements = job.getResourceRequirements();
        if (dbRequirements != null) {
            for (JobResourceRequirement req : dbRequirements) {
                requirements.put(req.getResourcePool().getPoolName(), req.getRequiredCapacity());
            }
        }
        
        // Set default requirements if not specified
        if (requirements.isEmpty()) {
            requirements.put("CPU", 1);
            requirements.put("MEMORY", 1024); // 1GB default
        }
        
        return requirements;
    }

    /**
     * Parses JSON parameters string to Map.
     */
    private Map<String, Object> parseJsonParameters(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse JSON parameters", e);
            return new HashMap<>();
        }
    }

    /**
     * Converts Symphony metrics to internal format.
     */
    private Map<String, Object> convertMetrics(SymphonyJobMetrics symphonyMetrics) {
        Map<String, Object> metrics = new HashMap<>();
        
        if (symphonyMetrics != null) {
            metrics.put("cpuTime", symphonyMetrics.getCpuTimeSeconds());
            metrics.put("memoryUsed", symphonyMetrics.getMemoryUsedMB());
            metrics.put("diskIO", symphonyMetrics.getDiskIOMB());
            metrics.put("networkIO", symphonyMetrics.getNetworkIOMB());
            metrics.put("executionNode", symphonyMetrics.getExecutionNode());
        }
        
        return metrics;
    }

    /**
     * Gets the current hostname.
     */
    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Gets the current process ID.
     */
    private int getProcessId() {
        return (int) ProcessHandle.current().pid();
    }

    /**
     * Converts exception to stack trace string.
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}