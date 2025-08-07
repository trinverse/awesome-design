# Batch Orchestrator Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
3. [Database Setup](#database-setup)
4. [Application Deployment](#application-deployment)
5. [Symphony Grid Integration](#symphony-grid-integration)
6. [Migration from Autosys](#migration-from-autosys)
7. [Monitoring Setup](#monitoring-setup)
8. [Security Configuration](#security-configuration)
9. [Operational Procedures](#operational-procedures)
10. [Troubleshooting](#troubleshooting)

## Prerequisites

### System Requirements
- **Operating System**: Red Hat Enterprise Linux 7+ or Windows Server 2019+
- **Java**: OpenJDK 17 or Oracle JDK 17
- **Database**: SQL Server 2019+ with Always On Availability Groups
- **Memory**: Minimum 8GB RAM per node (16GB recommended)
- **Storage**: 100GB for application and logs
- **Network**: Low latency connection to database and Symphony Grid

### Software Dependencies
```bash
# Java 17
java -version

# Maven (for building)
mvn -version

# Python 3.8+ (for migration scripts)
python3 --version
```

## Infrastructure Setup

### 1. VM Provisioning

#### Production Environment (3-node cluster)
```yaml
Node 1 (Primary):
  CPU: 8 cores
  Memory: 16GB
  Storage: 200GB
  Network: 10Gbps
  
Node 2 (Secondary):
  CPU: 8 cores
  Memory: 16GB
  Storage: 200GB
  Network: 10Gbps
  
Node 3 (Tertiary):
  CPU: 8 cores
  Memory: 16GB
  Storage: 200GB
  Network: 10Gbps
```

### 2. Load Balancer Configuration

```nginx
upstream batch_orchestrator {
    least_conn;
    server node1.bank.com:8080 weight=3;
    server node2.bank.com:8080 weight=2;
    server node3.bank.com:8080 weight=1;
    
    keepalive 32;
}

server {
    listen 443 ssl;
    server_name batch.bank.com;
    
    ssl_certificate /etc/ssl/certs/bank.crt;
    ssl_certificate_key /etc/ssl/private/bank.key;
    
    location / {
        proxy_pass http://batch_orchestrator;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket support for real-time monitoring
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## Database Setup

### 1. Create Database and User

```sql
-- Create database
CREATE DATABASE BatchOrchestrator
ON PRIMARY (
    NAME = 'BatchOrchestrator_Data',
    FILENAME = 'D:\SQLData\BatchOrchestrator.mdf',
    SIZE = 10GB,
    FILEGROWTH = 1GB
)
LOG ON (
    NAME = 'BatchOrchestrator_Log',
    FILENAME = 'E:\SQLLogs\BatchOrchestrator.ldf',
    SIZE = 5GB,
    FILEGROWTH = 500MB
);
GO

-- Create login and user
CREATE LOGIN batch_user WITH PASSWORD = 'Strong_P@ssw0rd_2024!';
GO

USE BatchOrchestrator;
GO

CREATE USER batch_user FOR LOGIN batch_user;
GO

-- Grant permissions
ALTER ROLE db_owner ADD MEMBER batch_user;
GO
```

### 2. Run Schema Creation

```bash
# Execute schema creation script
sqlcmd -S localhost -d BatchOrchestrator -U batch_user -P 'Strong_P@ssw0rd_2024!' -i database-schema.sql
```

### 3. Configure Always On Availability Groups

```sql
-- Create availability group
CREATE AVAILABILITY GROUP BatchOrchestratorAG
WITH (
    AUTOMATED_BACKUP_PREFERENCE = SECONDARY,
    DB_FAILOVER = ON,
    DTC_SUPPORT = PER_DB
)
FOR DATABASE BatchOrchestrator
REPLICA ON 
    'SQL-NODE1' WITH (
        ENDPOINT_URL = 'TCP://sql-node1.bank.com:5022',
        AVAILABILITY_MODE = SYNCHRONOUS_COMMIT,
        FAILOVER_MODE = AUTOMATIC,
        SECONDARY_ROLE (ALLOW_CONNECTIONS = READ_ONLY)
    ),
    'SQL-NODE2' WITH (
        ENDPOINT_URL = 'TCP://sql-node2.bank.com:5022',
        AVAILABILITY_MODE = SYNCHRONOUS_COMMIT,
        FAILOVER_MODE = AUTOMATIC,
        SECONDARY_ROLE (ALLOW_CONNECTIONS = READ_ONLY)
    );
```

## Application Deployment

### 1. Build Application

```bash
# Clone repository
git clone https://github.bank.com/batch/orchestrator.git
cd orchestrator

# Build with Maven
mvn clean package -Pproduction

# Output JAR will be in target/batch-orchestrator-1.0.0.jar
```

### 2. Create Deployment Structure

```bash
# Create application directories
sudo mkdir -p /opt/batch-orchestrator/{bin,config,lib,logs,scripts}
sudo chown -R batch:batch /opt/batch-orchestrator

# Copy artifacts
cp target/batch-orchestrator-1.0.0.jar /opt/batch-orchestrator/lib/
cp application.yml /opt/batch-orchestrator/config/
cp scripts/* /opt/batch-orchestrator/scripts/
```

### 3. Create Systemd Service

```ini
# /etc/systemd/system/batch-orchestrator.service
[Unit]
Description=Batch Orchestrator Service
After=network.target

[Service]
Type=simple
User=batch
Group=batch
WorkingDirectory=/opt/batch-orchestrator
Environment="JAVA_OPTS=-Xms4g -Xmx8g -XX:+UseG1GC"
Environment="SPRING_PROFILES_ACTIVE=production"
Environment="DB_PASSWORD_FILE=/etc/batch-orchestrator/db.password"
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/batch-orchestrator/lib/batch-orchestrator-1.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 4. Start Service

```bash
# Enable and start service
sudo systemctl enable batch-orchestrator
sudo systemctl start batch-orchestrator

# Check status
sudo systemctl status batch-orchestrator

# View logs
sudo journalctl -u batch-orchestrator -f
```

## Symphony Grid Integration

### 1. Configure Symphony Client

```yaml
# symphony-config.yml
symphony:
  master:
    url: https://symphony-master.bank.com:8080
    api-key: ${SYMPHONY_API_KEY}
  
  resource-manager:
    url: https://symphony-rm.bank.com:8081
    
  job-submission:
    queue: BATCH_QUEUE
    priority-levels: 10
    
  ssl:
    keystore: /opt/batch-orchestrator/config/symphony.jks
    keystore-password: ${KEYSTORE_PASSWORD}
    truststore: /opt/batch-orchestrator/config/truststore.jks
    truststore-password: ${TRUSTSTORE_PASSWORD}
```

### 2. Test Symphony Connection

```bash
# Test connectivity
curl -X GET https://symphony-master.bank.com:8080/api/v1/health \
  -H "X-API-Key: ${SYMPHONY_API_KEY}"

# Submit test job
curl -X POST https://symphony-master.bank.com:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${SYMPHONY_API_KEY}" \
  -d '{
    "jobName": "TEST_JOB",
    "command": "echo Hello Symphony",
    "resources": {"cpu": 1, "memory": 1024}
  }'
```

## Migration from Autosys

### 1. Export Autosys Jobs

```bash
# Export all jobs to JIL format
autorep -J ALL -q > all_jobs.jil

# Export specific job box
autorep -J DAILY_BATCH_BOX -q > daily_batch.jil
```

### 2. Run Migration Script

```bash
# Convert JIL to SQL
python3 migration/autosys-migration.py all_jobs.jil -o migration.sql

# Review migration report
cat migration_report.json
```

### 3. Validate Migration

```sql
-- Check migrated jobs
SELECT job_name, job_type, job_group, is_active
FROM job_definitions
WHERE created_by = 'MIGRATION'
ORDER BY job_id;

-- Verify schedules
SELECT j.job_name, s.schedule_type, s.cron_expression
FROM job_schedules s
JOIN job_definitions j ON s.job_id = j.job_id
WHERE s.created_by = 'MIGRATION';

-- Check dependencies
SELECT 
    j1.job_name as job,
    j2.job_name as depends_on,
    d.dependency_type
FROM job_dependencies d
JOIN job_definitions j1 ON d.job_id = j1.job_id
JOIN job_definitions j2 ON d.dependent_job_id = j2.job_id;
```

### 4. Parallel Run Phase

```bash
# Keep Autosys running but in monitor mode
sendevent -E STOP_DEMON

# Enable shadow mode in new system
curl -X POST https://batch.bank.com/api/v1/admin/shadow-mode \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{"enabled": true, "compareWith": "AUTOSYS"}'
```

## Monitoring Setup

### 1. Prometheus Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'batch-orchestrator'
    metrics_path: '/batch-orchestrator/actuator/prometheus'
    static_configs:
      - targets:
        - 'node1.bank.com:8080'
        - 'node2.bank.com:8080'
        - 'node3.bank.com:8080'
```

### 2. Grafana Dashboard

Import the provided dashboard JSON:
```json
{
  "dashboard": {
    "title": "Batch Orchestrator Monitoring",
    "panels": [
      {
        "title": "Job Execution Rate",
        "targets": [{
          "expr": "rate(batch_jobs_submitted_total[5m])"
        }]
      },
      {
        "title": "Success Rate",
        "targets": [{
          "expr": "rate(batch_jobs_completed_total[5m]) / rate(batch_jobs_submitted_total[5m])"
        }]
      },
      {
        "title": "Running Jobs",
        "targets": [{
          "expr": "batch_jobs_running"
        }]
      }
    ]
  }
}
```

### 3. Alert Rules

```yaml
# alerts.yml
groups:
  - name: batch_orchestrator
    rules:
      - alert: HighJobFailureRate
        expr: rate(batch_jobs_failed_total[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High job failure rate detected"
          
      - alert: LongRunningJob
        expr: batch_job_execution_time > 7200
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Job running for more than 2 hours"
```

## Security Configuration

### 1. SSL/TLS Setup

```bash
# Generate keystore
keytool -genkeypair -alias batch-orchestrator \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -keystore batch-orchestrator.jks \
  -storepass changeit

# Configure in application.yml
server:
  ssl:
    key-store: /opt/batch-orchestrator/config/batch-orchestrator.jks
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: JKS
    key-alias: batch-orchestrator
```

### 2. LDAP Integration

```yaml
spring:
  security:
    ldap:
      urls: ldaps://ldap.bank.com:636
      base: dc=bank,dc=com
      username: cn=batch,ou=service,dc=bank,dc=com
      password: ${LDAP_PASSWORD}
      user-search-base: ou=users
      user-search-filter: (uid={0})
      group-search-base: ou=groups
      group-search-filter: (member={0})
```

### 3. API Authentication

```bash
# Generate API keys for service accounts
curl -X POST https://batch.bank.com/api/v1/auth/api-keys \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "service": "monitoring-system",
    "permissions": ["VIEW_JOBS", "VIEW_EXECUTIONS"]
  }'
```

## Operational Procedures

### Daily Operations

```bash
# Check system health
curl https://batch.bank.com/actuator/health

# View running jobs
curl https://batch.bank.com/api/v1/jobs/running \
  -H "Authorization: Bearer ${TOKEN}"

# Check failed jobs in last 24 hours
curl "https://batch.bank.com/api/v1/jobs/executions?status=FAILURE&startDate=$(date -d '24 hours ago' --iso-8601)" \
  -H "Authorization: Bearer ${TOKEN}"
```

### Backup Procedures

```bash
#!/bin/bash
# backup.sh

# Database backup
sqlcmd -S sql-cluster.bank.com -d BatchOrchestrator \
  -Q "BACKUP DATABASE BatchOrchestrator TO DISK='D:\Backups\BatchOrchestrator_$(date +%Y%m%d).bak' WITH COMPRESSION"

# Configuration backup
tar -czf /backup/batch-config-$(date +%Y%m%d).tar.gz /opt/batch-orchestrator/config/

# Upload to secure storage
aws s3 cp /backup/batch-config-$(date +%Y%m%d).tar.gz s3://bank-backups/batch-orchestrator/
```

### Disaster Recovery

```bash
# Failover to DR site
curl -X POST https://batch-dr.bank.com/api/v1/admin/activate \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# Restore database
sqlcmd -S sql-dr.bank.com -d master \
  -Q "RESTORE DATABASE BatchOrchestrator FROM DISK='D:\Backups\BatchOrchestrator_latest.bak' WITH REPLACE"

# Sync job configurations
rsync -avz /opt/batch-orchestrator/config/ dr-node:/opt/batch-orchestrator/config/
```

## Troubleshooting

### Common Issues

#### 1. Jobs Not Executing
```bash
# Check scheduler status
curl https://batch.bank.com/api/v1/admin/scheduler/status

# View scheduler logs
grep "scheduleJobs" /opt/batch-orchestrator/logs/batch-orchestrator.log

# Verify database connectivity
sqlcmd -S sql-cluster.bank.com -d BatchOrchestrator -Q "SELECT COUNT(*) FROM job_definitions"
```

#### 2. Symphony Grid Connection Issues
```bash
# Test Symphony connectivity
telnet symphony-master.bank.com 8080

# Check Symphony logs
ssh symphony-master "tail -f /var/log/symphony/master.log"

# Verify API key
curl -X GET https://symphony-master.bank.com/api/v1/validate \
  -H "X-API-Key: ${SYMPHONY_API_KEY}"
```

#### 3. High Memory Usage
```bash
# Generate heap dump
jmap -dump:format=b,file=/tmp/heap.hprof $(pgrep -f batch-orchestrator)

# Analyze with Eclipse MAT or jhat
jhat /tmp/heap.hprof

# Adjust JVM settings if needed
export JAVA_OPTS="-Xms4g -Xmx12g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Performance Tuning

```yaml
# Optimize database connections
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 20000
      
# Tune executor threads
batch:
  orchestrator:
    executor:
      core-pool-size: 25
      max-pool-size: 100
      queue-capacity: 5000
      
# Adjust Quartz settings
spring:
  quartz:
    properties:
      org:
        quartz:
          threadPool:
            threadCount: 50
```

## Support Contacts

- **Production Support**: batch-support@bank.com
- **Development Team**: batch-dev@bank.com
- **On-Call**: +1-555-BATCH-911
- **Slack Channel**: #batch-orchestrator
- **Wiki**: https://wiki.bank.com/batch-orchestrator