package com.bank.batchorchestrator.repository;

import com.bank.batchorchestrator.entity.JobExecution;
import com.bank.batchorchestrator.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    Optional<JobExecution> findByExecutionId(String executionId);
    
    List<JobExecution> findByJobIdOrderByStartTimeDesc(Long jobId);
    
    Page<JobExecution> findByStatus(JobStatus status, Pageable pageable);
    
    @Query("SELECT je FROM JobExecution je WHERE je.status IN :statuses")
    List<JobExecution> findByStatusIn(@Param("statuses") List<JobStatus> statuses);
    
    @Query("SELECT je FROM JobExecution je WHERE je.job.id = :jobId AND je.startTime >= :since")
    List<JobExecution> findRecentExecutions(@Param("jobId") Long jobId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(je) FROM JobExecution je WHERE je.status = :status")
    long countByStatus(@Param("status") JobStatus status);
    
    @Query("SELECT je FROM JobExecution je WHERE je.status = 'RUNNING' AND je.startTime < :timeout")
    List<JobExecution> findTimedOutExecutions(@Param("timeout") LocalDateTime timeout);
}