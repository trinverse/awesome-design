package com.bank.batchorchestrator.controller;

import com.bank.batchorchestrator.model.AlertDto;
import com.bank.batchorchestrator.model.MetricsDto;
import com.bank.batchorchestrator.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "APIs for monitoring and alerting")
public class MonitoringController {
    private final MonitoringService monitoringService;
    
    @GetMapping("/metrics")
    @Operation(summary = "Get system metrics")
    public ResponseEntity<MetricsDto> getMetrics() {
        MetricsDto metrics = monitoringService.getMetrics();
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/health")
    @Operation(summary = "Get system health status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = monitoringService.getHealthStatus();
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/alerts")
    @Operation(summary = "Get open alerts")
    public ResponseEntity<List<AlertDto>> getOpenAlerts() {
        List<AlertDto> alerts = monitoringService.getOpenAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    @GetMapping("/alerts/critical")
    @Operation(summary = "Get critical alerts")
    public ResponseEntity<List<AlertDto>> getCriticalAlerts() {
        List<AlertDto> alerts = monitoringService.getCriticalAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    @PutMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    public ResponseEntity<AlertDto> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestParam String acknowledgedBy) {
        AlertDto alert = monitoringService.acknowledgeAlert(alertId, acknowledgedBy);
        return ResponseEntity.ok(alert);
    }
    
    @PutMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<AlertDto> resolveAlert(
            @PathVariable Long alertId,
            @RequestParam String resolvedBy) {
        AlertDto alert = monitoringService.resolveAlert(alertId, resolvedBy);
        return ResponseEntity.ok(alert);
    }
}