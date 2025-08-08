package com.bank.batchorchestrator.config;

import com.bank.batchorchestrator.entity.Job;
import com.bank.batchorchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {
    
    @Bean
    CommandLineRunner init(JobRepository jobRepository) {
        return args -> {
            log.info("Initializing sample jobs...");
            
            // Create sample jobs for demonstration
            createSampleJob(jobRepository, "daily-report", 
                    "Generate daily reports", 
                    "echo 'Generating daily report...'",
                    "0 0 2 * * ?", // Every day at 2 AM
                    5);
            
            createSampleJob(jobRepository, "data-backup", 
                    "Backup database", 
                    "echo 'Backing up database...'",
                    "0 0 3 * * ?", // Every day at 3 AM
                    10);
            
            createSampleJob(jobRepository, "email-notifications", 
                    "Send email notifications", 
                    "echo 'Sending email notifications...'",
                    "0 */30 * * * ?", // Every 30 minutes
                    3);
            
            createSampleJob(jobRepository, "cleanup-logs", 
                    "Clean up old log files", 
                    "echo 'Cleaning up logs older than 30 days...'",
                    "0 0 4 * * SUN", // Every Sunday at 4 AM
                    5);
            
            createSampleJob(jobRepository, "health-check", 
                    "System health check", 
                    "echo 'Performing system health check...'",
                    "0 */15 * * * ?", // Every 15 minutes
                    1);
            
            // Create jobs with dependencies
            Job etlExtract = createSampleJob(jobRepository, "etl-extract", 
                    "Extract data from source systems", 
                    "echo 'Extracting data from source systems...'",
                    "0 0 1 * * ?", // Every day at 1 AM
                    8);
            
            Job etlTransform = createSampleJob(jobRepository, "etl-transform", 
                    "Transform extracted data", 
                    "echo 'Transforming data...'",
                    "", // Empty schedule for dependency-triggered jobs
                    7);
            
            Job etlLoad = createSampleJob(jobRepository, "etl-load", 
                    "Load data into data warehouse", 
                    "echo 'Loading data into warehouse...'",
                    "", // Empty schedule for dependency-triggered jobs
                    9);
            
            // Set up dependencies
            if (etlExtract != null && etlTransform != null && etlLoad != null) {
                etlTransform.getDependencies().add(etlExtract);
                jobRepository.save(etlTransform);
                
                etlLoad.getDependencies().add(etlTransform);
                jobRepository.save(etlLoad);
            }
            
            log.info("Sample jobs initialized successfully");
        };
    }
    
    private Job createSampleJob(JobRepository repository, String name, String description, 
                                String command, String schedule, Integer priority) {
        if (repository.findByJobName(name).isPresent()) {
            log.info("Job {} already exists, skipping...", name);
            return repository.findByJobName(name).get();
        }
        
        Map<String, String> parameters = new HashMap<>();
        parameters.put("env", "development");
        parameters.put("debug", "true");
        
        Job job = Job.builder()
                .jobName(name)
                .description(description)
                .jobType("SHELL")
                .command(command)
                .schedule(schedule)
                .active(true)
                .maxRetries(3)
                .timeoutMinutes(30)
                .priority(priority)
                .parameters(parameters)
                .createdBy("system")
                .modifiedBy("system")
                .build();
        
        Job savedJob = repository.save(job);
        log.info("Created sample job: {}", name);
        
        return savedJob;
    }
}