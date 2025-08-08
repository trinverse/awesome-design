package com.bank.batchorchestrator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_execution_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private JobExecution execution;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;
    
    @Column(nullable = false, length = 5000)
    private String message;
    
    private String source;
    
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, FATAL
    }
}