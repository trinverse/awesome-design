package com.bank.batchorchestrator.model;

import com.bank.batchorchestrator.entity.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionDto {
    private Long id;
    private String executionId;
    private Long jobId;
    private String jobName;
    private JobStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMillis;
    private String output;
    private String errorMessage;
    private Integer exitCode;
    private Integer retryCount;
    private String triggeredBy;
    private String executionHost;
    private Map<String, String> executionParameters;
}