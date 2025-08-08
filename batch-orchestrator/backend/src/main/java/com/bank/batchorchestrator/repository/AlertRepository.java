package com.bank.batchorchestrator.repository;

import com.bank.batchorchestrator.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatusOrderByCreatedAtDesc(Alert.AlertStatus status);
    
    List<Alert> findByJobIdAndStatus(Long jobId, Alert.AlertStatus status);
    
    List<Alert> findBySeverityAndStatus(Alert.AlertSeverity severity, Alert.AlertStatus status);
}