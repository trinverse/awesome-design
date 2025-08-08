package com.bank.batchorchestrator.service;

import com.bank.batchorchestrator.entity.JobExecution;
import com.bank.batchorchestrator.entity.JobExecutionLog;
import com.bank.batchorchestrator.entity.JobStatus;
import com.bank.batchorchestrator.model.JobExecutionDto;
import com.bank.batchorchestrator.repository.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionEngine {
    private final JobExecutionRepository jobExecutionRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(50);
    
    @Value("${app.job.default-timeout-minutes:120}")
    private int defaultTimeoutMinutes;
    
    public CompletableFuture<JobExecutionDto> executeJob(JobExecution execution) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return runJob(execution);
            } catch (Exception e) {
                log.error("Error executing job: {}", execution.getExecutionId(), e);
                throw new CompletionException(e);
            }
        }, executorService);
    }
    
    private JobExecutionDto runJob(JobExecution execution) throws Exception {
        log.info("Starting execution: {} for job: {}", 
                execution.getExecutionId(), execution.getJob().getJobName());
        
        // Update status to RUNNING
        execution.setStatus(JobStatus.RUNNING);
        execution.setStartTime(LocalDateTime.now());
        execution.setExecutionHost(InetAddress.getLocalHost().getHostName());
        jobExecutionRepository.save(execution);
        
        try {
            // Build command with parameters
            String command = buildCommand(execution.getJob().getCommand(), execution.getExecutionParameters());
            
            // Execute based on job type
            ExecutionResult result = executeCommand(command, execution.getJob().getTimeoutMinutes());
            
            // Update execution with results
            execution.setStatus(result.exitCode == 0 ? JobStatus.SUCCESS : JobStatus.FAILED);
            execution.setExitCode(result.exitCode);
            execution.setOutput(result.output);
            execution.setErrorMessage(result.error);
            execution.setEndTime(LocalDateTime.now());
            execution.setDurationMillis(
                    java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
            );
            
            // Add execution logs
            addExecutionLogs(execution, result);
            
        } catch (TimeoutException e) {
            log.error("Job execution timed out: {}", execution.getExecutionId());
            execution.setStatus(JobStatus.TIMEOUT);
            execution.setErrorMessage("Job execution timed out");
            execution.setEndTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Job execution failed: {}", execution.getExecutionId(), e);
            execution.setStatus(JobStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setEndTime(LocalDateTime.now());
        }
        
        // Save final state
        JobExecution savedExecution = jobExecutionRepository.save(execution);
        
        return convertToDto(savedExecution);
    }
    
    private String buildCommand(String baseCommand, Map<String, String> parameters) {
        String command = baseCommand;
        
        // Replace parameter placeholders
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            command = command.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        
        return command;
    }
    
    private ExecutionResult executeCommand(String command, Integer timeoutMinutes) throws Exception {
        int timeout = timeoutMinutes != null ? timeoutMinutes : defaultTimeoutMinutes;
        
        log.debug("Executing command: {} with timeout: {} minutes", command, timeout);
        
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // Handle different OS
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }
        
        Process process = processBuilder.start();
        
        // Read output
        Future<String> outputFuture = executorService.submit(() -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                log.error("Error reading process output", e);
            }
            return output.toString();
        });
        
        // Read error
        Future<String> errorFuture = executorService.submit(() -> {
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            } catch (Exception e) {
                log.error("Error reading process error stream", e);
            }
            return error.toString();
        });
        
        // Wait for process with timeout
        boolean finished = process.waitFor(timeout, TimeUnit.MINUTES);
        
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Process execution timed out");
        }
        
        String output = outputFuture.get(1, TimeUnit.MINUTES);
        String error = errorFuture.get(1, TimeUnit.MINUTES);
        int exitCode = process.exitValue();
        
        return new ExecutionResult(exitCode, output, error);
    }
    
    private void addExecutionLogs(JobExecution execution, ExecutionResult result) {
        List<JobExecutionLog> logs = new ArrayList<>();
        
        // Add start log
        logs.add(JobExecutionLog.builder()
                .execution(execution)
                .level(JobExecutionLog.LogLevel.INFO)
                .message("Job execution started")
                .source("ENGINE")
                .build());
        
        // Add output log if present
        if (result.output != null && !result.output.trim().isEmpty()) {
            logs.add(JobExecutionLog.builder()
                    .execution(execution)
                    .level(JobExecutionLog.LogLevel.INFO)
                    .message("Output: " + truncate(result.output, 5000))
                    .source("JOB_OUTPUT")
                    .build());
        }
        
        // Add error log if present
        if (result.error != null && !result.error.trim().isEmpty()) {
            logs.add(JobExecutionLog.builder()
                    .execution(execution)
                    .level(JobExecutionLog.LogLevel.ERROR)
                    .message("Error: " + truncate(result.error, 5000))
                    .source("JOB_ERROR")
                    .build());
        }
        
        // Add completion log
        logs.add(JobExecutionLog.builder()
                .execution(execution)
                .level(result.exitCode == 0 ? JobExecutionLog.LogLevel.INFO : JobExecutionLog.LogLevel.ERROR)
                .message("Job execution completed with exit code: " + result.exitCode)
                .source("ENGINE")
                .build());
        
        execution.setLogs(logs);
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
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
    
    private static class ExecutionResult {
        final int exitCode;
        final String output;
        final String error;
        
        ExecutionResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }
}