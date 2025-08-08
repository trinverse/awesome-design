package com.bank.batchorchestrator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String jobName;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private String jobType; // SHELL, JAVA, PYTHON, etc.
    
    @Column(nullable = false, length = 2000)
    private String command;
    
    @Column(nullable = false)
    private String schedule; // Cron expression
    
    @Column(nullable = false)
    private Boolean active = true;
    
    private Integer maxRetries = 3;
    
    private Integer timeoutMinutes = 120;
    
    private Integer priority = 5; // 1-10, 10 being highest
    
    @ElementCollection
    @CollectionTable(name = "job_parameters")
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
    
    @ManyToMany
    @JoinTable(
        name = "job_dependencies",
        joinColumns = @JoinColumn(name = "job_id"),
        inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    @Builder.Default
    private List<Job> dependencies = new ArrayList<>();
    
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL)
    @Builder.Default
    private List<JobExecution> executions = new ArrayList<>();
    
    @Column(nullable = false)
    private String createdBy;
    
    @Column(nullable = false)
    private String modifiedBy;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
}