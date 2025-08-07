package com.bank.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the Batch Orchestrator System.
 * This enterprise-grade batch job orchestration system replaces Autosys
 * with a modern, database-driven, and scalable architecture.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableTransactionManagement
public class BatchOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchOrchestratorApplication.class, args);
    }
}