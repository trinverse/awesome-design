package com.bank.batchorchestrator.repository;

import com.bank.batchorchestrator.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByJobName(String jobName);
    
    List<Job> findByActiveTrue();
    
    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.dependencies WHERE j.jobName = :jobName")
    Optional<Job> findByJobNameWithDependencies(@Param("jobName") String jobName);
    
    @Query("SELECT j FROM Job j WHERE j.active = true AND j.schedule IS NOT NULL")
    List<Job> findScheduledJobs();
    
    @Query("SELECT j FROM Job j WHERE :dependency MEMBER OF j.dependencies")
    List<Job> findJobsWithDependency(@Param("dependency") Job dependency);
}