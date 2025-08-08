package com.bank.batchorchestrator.entity;

public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT,
    RETRYING,
    SKIPPED,
    WAITING_DEPENDENCY
}