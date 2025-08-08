package com.bank.batchorchestrator.service;

import com.bank.batchorchestrator.entity.Job;
import com.bank.batchorchestrator.exception.JobNotFoundException;
import com.bank.batchorchestrator.model.JobDto;
import com.bank.batchorchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobConfigurationService {
    private final JobRepository jobRepository;
    
    @Transactional
    public JobDto createJob(JobDto jobDto) {
        log.info("Creating new job: {}", jobDto.getJobName());
        
        Job job = Job.builder()
                .jobName(jobDto.getJobName())
                .description(jobDto.getDescription())
                .jobType(jobDto.getJobType())
                .command(jobDto.getCommand())
                .schedule(jobDto.getSchedule())
                .active(jobDto.getActive() != null ? jobDto.getActive() : true)
                .maxRetries(jobDto.getMaxRetries() != null ? jobDto.getMaxRetries() : 3)
                .timeoutMinutes(jobDto.getTimeoutMinutes() != null ? jobDto.getTimeoutMinutes() : 120)
                .priority(jobDto.getPriority() != null ? jobDto.getPriority() : 5)
                .parameters(jobDto.getParameters())
                .createdBy(jobDto.getCreatedBy())
                .modifiedBy(jobDto.getCreatedBy())
                .build();
        
        Job savedJob = jobRepository.save(job);
        log.info("Job created successfully with ID: {}", savedJob.getId());
        
        return convertToDto(savedJob);
    }
    
    @Transactional
    public JobDto updateJob(Long jobId, JobDto jobDto) {
        log.info("Updating job with ID: {}", jobId);
        
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        
        job.setJobName(jobDto.getJobName());
        job.setDescription(jobDto.getDescription());
        job.setJobType(jobDto.getJobType());
        job.setCommand(jobDto.getCommand());
        job.setSchedule(jobDto.getSchedule());
        job.setActive(jobDto.getActive());
        job.setMaxRetries(jobDto.getMaxRetries());
        job.setTimeoutMinutes(jobDto.getTimeoutMinutes());
        job.setPriority(jobDto.getPriority());
        job.setParameters(jobDto.getParameters());
        job.setModifiedBy(jobDto.getModifiedBy());
        job.setUpdatedAt(LocalDateTime.now());
        
        Job updatedJob = jobRepository.save(job);
        log.info("Job updated successfully");
        
        return convertToDto(updatedJob);
    }
    
    @Transactional(readOnly = true)
    public JobDto getJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        return convertToDto(job);
    }
    
    @Transactional(readOnly = true)
    public JobDto getJobByName(String jobName) {
        Job job = jobRepository.findByJobName(jobName)
                .orElseThrow(() -> new JobNotFoundException("Job not found with name: " + jobName));
        return convertToDto(job);
    }
    
    @Transactional(readOnly = true)
    public List<JobDto> getAllJobs() {
        return jobRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<JobDto> getActiveJobs() {
        return jobRepository.findByActiveTrue().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteJob(Long jobId) {
        log.info("Deleting job with ID: {}", jobId);
        
        if (!jobRepository.existsById(jobId)) {
            throw new JobNotFoundException("Job not found with ID: " + jobId);
        }
        
        jobRepository.deleteById(jobId);
        log.info("Job deleted successfully");
    }
    
    @Transactional
    public void toggleJobStatus(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        
        job.setActive(!job.getActive());
        jobRepository.save(job);
        
        log.info("Job {} status changed to: {}", jobId, job.getActive() ? "ACTIVE" : "INACTIVE");
    }
    
    @Transactional
    public void addDependency(Long jobId, Long dependencyId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        
        Job dependency = jobRepository.findById(dependencyId)
                .orElseThrow(() -> new JobNotFoundException("Dependency job not found with ID: " + dependencyId));
        
        if (!job.getDependencies().contains(dependency)) {
            job.getDependencies().add(dependency);
            jobRepository.save(job);
            log.info("Added dependency {} to job {}", dependencyId, jobId);
        }
    }
    
    @Transactional
    public void removeDependency(Long jobId, Long dependencyId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        
        job.getDependencies().removeIf(dep -> dep.getId().equals(dependencyId));
        jobRepository.save(job);
        
        log.info("Removed dependency {} from job {}", dependencyId, jobId);
    }
    
    private JobDto convertToDto(Job job) {
        return JobDto.builder()
                .id(job.getId())
                .jobName(job.getJobName())
                .description(job.getDescription())
                .jobType(job.getJobType())
                .command(job.getCommand())
                .schedule(job.getSchedule())
                .active(job.getActive())
                .maxRetries(job.getMaxRetries())
                .timeoutMinutes(job.getTimeoutMinutes())
                .priority(job.getPriority())
                .parameters(job.getParameters())
                .dependencyIds(job.getDependencies().stream()
                        .map(Job::getId)
                        .collect(Collectors.toList()))
                .createdBy(job.getCreatedBy())
                .modifiedBy(job.getModifiedBy())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}