package com.bank.batchorchestrator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "job_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String executionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    private Long durationMillis;
    
    @Column(length = 5000)
    private String output;
    
    @Column(length = 5000)
    private String errorMessage;
    
    private Integer exitCode;
    
    private Integer retryCount = 0;
    
    @Column(nullable = false)
    private String triggeredBy; // SCHEDULER, MANUAL, API, DEPENDENCY
    
    private String executionHost;
    
    private String executionNode;
    
    @ElementCollection
    @CollectionTable(name = "execution_parameters")
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    private Map<String, String> executionParameters = new HashMap<>();
    
    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL)
    private List<JobExecutionLog> logs = new ArrayList<>();
    
    private LocalDateTime createdAt = LocalDateTime.now();
}