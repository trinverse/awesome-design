package com.bank.batchorchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDto {
    private long totalJobsExecuted;
    private long successfulJobs;
    private long failedJobs;
    private long timedOutJobs;
    private long cancelledJobs;
    private long runningJobs;
    private long queuedJobs;
    private long waitingJobs;
    private double successRate;
    private LocalDateTime timestamp;
}