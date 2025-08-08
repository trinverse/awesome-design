package com.bank.batchorchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {
    private Long id;
    private String jobName;
    private String description;
    private String jobType;
    private String command;
    private String schedule;
    private Boolean active;
    private Integer maxRetries;
    private Integer timeoutMinutes;
    private Integer priority;
    private Map<String, String> parameters;
    private List<Long> dependencyIds;
    private String createdBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}