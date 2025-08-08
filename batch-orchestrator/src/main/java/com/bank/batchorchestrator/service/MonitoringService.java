package com.bank.batchorchestrator.service;

import com.bank.batchorchestrator.entity.Alert;
import com.bank.batchorchestrator.entity.JobExecution;
import com.bank.batchorchestrator.entity.JobStatus;
import com.bank.batchorchestrator.model.AlertDto;
import com.bank.batchorchestrator.model.MetricsDto;
import com.bank.batchorchestrator.repository.AlertRepository;
import com.bank.batchorchestrator.repository.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {
    private final JobExecutionRepository jobExecutionRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Metrics counters
    private final AtomicLong totalJobsExecuted = new AtomicLong(0);
    private final AtomicLong successfulJobs = new AtomicLong(0);
    private final AtomicLong failedJobs = new AtomicLong(0);
    private final AtomicLong timedOutJobs = new AtomicLong(0);
    private final AtomicLong cancelledJobs = new AtomicLong(0);
    
    @Transactional
    public void recordJobSuccess(JobExecution execution) {
        totalJobsExecuted.incrementAndGet();
        successfulJobs.incrementAndGet();
        
        log.info("Job {} completed successfully in {} ms", 
                execution.getJob().getJobName(), execution.getDurationMillis());
        
        // Send real-time update via WebSocket
        sendJobStatusUpdate(execution);
    }
    
    @Transactional
    public void recordJobFailure(JobExecution execution) {
        totalJobsExecuted.incrementAndGet();
        failedJobs.incrementAndGet();
        
        log.error("Job {} failed: {}", 
                execution.getJob().getJobName(), execution.getErrorMessage());
        
        // Create alert
        createAlert(
                Alert.AlertType.JOB_FAILED,
                Alert.AlertSeverity.HIGH,
                "Job Failed: " + execution.getJob().getJobName(),
                "Job execution failed with error: " + execution.getErrorMessage(),
                execution.getJob().getId(),
                execution.getId()
        );
        
        // Send real-time update via WebSocket
        sendJobStatusUpdate(execution);
    }
    
    @Transactional
    public void recordJobTimeout(JobExecution execution) {
        totalJobsExecuted.incrementAndGet();
        timedOutJobs.incrementAndGet();
        
        log.warn("Job {} timed out", execution.getJob().getJobName());
        
        // Create alert
        createAlert(
                Alert.AlertType.JOB_TIMEOUT,
                Alert.AlertSeverity.HIGH,
                "Job Timeout: " + execution.getJob().getJobName(),
                "Job execution exceeded timeout limit",
                execution.getJob().getId(),
                execution.getId()
        );
        
        // Send real-time update via WebSocket
        sendJobStatusUpdate(execution);
    }
    
    @Transactional
    public void recordJobCancellation(JobExecution execution) {
        cancelledJobs.incrementAndGet();
        
        log.info("Job {} was cancelled", execution.getJob().getJobName());
        
        // Send real-time update via WebSocket
        sendJobStatusUpdate(execution);
    }
    
    @Transactional(readOnly = true)
    public MetricsDto getMetrics() {
        long runningJobs = jobExecutionRepository.countByStatus(JobStatus.RUNNING);
        long queuedJobs = jobExecutionRepository.countByStatus(JobStatus.QUEUED);
        long waitingJobs = jobExecutionRepository.countByStatus(JobStatus.WAITING_DEPENDENCY);
        
        double successRate = totalJobsExecuted.get() > 0 
                ? (double) successfulJobs.get() / totalJobsExecuted.get() * 100 
                : 0;
        
        return MetricsDto.builder()
                .totalJobsExecuted(totalJobsExecuted.get())
                .successfulJobs(successfulJobs.get())
                .failedJobs(failedJobs.get())
                .timedOutJobs(timedOutJobs.get())
                .cancelledJobs(cancelledJobs.get())
                .runningJobs(runningJobs)
                .queuedJobs(queuedJobs)
                .waitingJobs(waitingJobs)
                .successRate(successRate)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        long runningJobs = jobExecutionRepository.countByStatus(JobStatus.RUNNING);
        long failedJobsLast24h = jobExecutionRepository.findByStatusIn(
                List.of(JobStatus.FAILED)
        ).stream()
                .filter(exec -> exec.getStartTime().isAfter(LocalDateTime.now().minusHours(24)))
                .count();
        
        health.put("status", failedJobsLast24h > 10 ? "DEGRADED" : "HEALTHY");
        health.put("runningJobs", runningJobs);
        health.put("failedJobsLast24h", failedJobsLast24h);
        health.put("timestamp", LocalDateTime.now());
        
        return health;
    }
    
    @Transactional
    public AlertDto createAlert(Alert.AlertType type, Alert.AlertSeverity severity, 
                                String title, String message, Long jobId, Long executionId) {
        Alert alert = Alert.builder()
                .type(type)
                .severity(severity)
                .title(title)
                .message(message)
                .jobId(jobId)
                .executionId(executionId)
                .status(Alert.AlertStatus.OPEN)
                .source("SYSTEM")
                .build();
        
        Alert savedAlert = alertRepository.save(alert);
        
        // Send alert via WebSocket
        sendAlertNotification(savedAlert);
        
        return convertToDto(savedAlert);
    }
    
    @Transactional
    public AlertDto acknowledgeAlert(Long alertId, String acknowledgedBy) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        
        alert.setStatus(Alert.AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(acknowledgedBy);
        
        Alert savedAlert = alertRepository.save(alert);
        return convertToDto(savedAlert);
    }
    
    @Transactional
    public AlertDto resolveAlert(Long alertId, String resolvedBy) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        
        alert.setStatus(Alert.AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);
        
        Alert savedAlert = alertRepository.save(alert);
        return convertToDto(savedAlert);
    }
    
    @Transactional(readOnly = true)
    public List<AlertDto> getOpenAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc(Alert.AlertStatus.OPEN).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AlertDto> getCriticalAlerts() {
        return alertRepository.findBySeverityAndStatus(
                Alert.AlertSeverity.CRITICAL, Alert.AlertStatus.OPEN
        ).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    private void sendJobStatusUpdate(JobExecution execution) {
        Map<String, Object> update = new HashMap<>();
        update.put("executionId", execution.getExecutionId());
        update.put("jobName", execution.getJob().getJobName());
        update.put("status", execution.getStatus());
        update.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/job-status", update);
    }
    
    private void sendAlertNotification(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts", convertToDto(alert));
    }
    
    private AlertDto convertToDto(Alert alert) {
        return AlertDto.builder()
                .id(alert.getId())
                .type(alert.getType())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .source(alert.getSource())
                .jobId(alert.getJobId())
                .executionId(alert.getExecutionId())
                .status(alert.getStatus())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .acknowledgedBy(alert.getAcknowledgedBy())
                .resolvedAt(alert.getResolvedAt())
                .resolvedBy(alert.getResolvedBy())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}