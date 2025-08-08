package com.bank.batchorchestrator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 2000)
    private String message;
    
    private String source;
    
    private Long jobId;
    
    private Long executionId;
    
    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.OPEN;
    
    private LocalDateTime acknowledgedAt;
    
    private String acknowledgedBy;
    
    private LocalDateTime resolvedAt;
    
    private String resolvedBy;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum AlertType {
        JOB_FAILED,
        JOB_TIMEOUT,
        DEPENDENCY_FAILED,
        SYSTEM_ERROR,
        PERFORMANCE_DEGRADATION,
        SLA_BREACH,
        CONFIGURATION_ERROR
    }
    
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum AlertStatus {
        OPEN, ACKNOWLEDGED, RESOLVED, IGNORED
    }
}