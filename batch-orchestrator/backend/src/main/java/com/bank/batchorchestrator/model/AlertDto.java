package com.bank.batchorchestrator.model;

import com.bank.batchorchestrator.entity.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {
    private Long id;
    private Alert.AlertType type;
    private Alert.AlertSeverity severity;
    private String title;
    private String message;
    private String source;
    private Long jobId;
    private Long executionId;
    private Alert.AlertStatus status;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private LocalDateTime createdAt;
}