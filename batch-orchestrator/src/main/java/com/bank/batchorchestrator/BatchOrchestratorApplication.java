package com.bank.batchorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BatchOrchestratorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BatchOrchestratorApplication.class, args);
    }
}