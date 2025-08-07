-- =============================================
-- Batch Job Orchestration System Database Schema
-- Version: 1.0.0
-- Database: SQL Server 2019+
-- =============================================

USE BatchOrchestrator;
GO

-- =============================================
-- Core Tables
-- =============================================

-- Job Definitions Table
CREATE TABLE job_definitions (
    job_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_name NVARCHAR(255) NOT NULL UNIQUE,
    job_group NVARCHAR(100) NOT NULL,
    job_type NVARCHAR(50) NOT NULL, -- SHELL, JAVA, PYTHON, STORED_PROC, FILE_WATCHER
    job_class_name NVARCHAR(500), -- For Java jobs
    script_path NVARCHAR(1000), -- For shell/python scripts
    stored_proc_name NVARCHAR(255), -- For stored procedures
    description NVARCHAR(MAX),
    is_active BIT DEFAULT 1,
    priority INT DEFAULT 5, -- 1-10, higher is more important
    max_retry_count INT DEFAULT 3,
    retry_interval_seconds INT DEFAULT 300,
    timeout_minutes INT DEFAULT 60,
    alert_on_failure BIT DEFAULT 1,
    alert_on_success BIT DEFAULT 0,
    alert_on_start BIT DEFAULT 0,
    critical_job BIT DEFAULT 0,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL,
    version INT DEFAULT 1,
    CONSTRAINT chk_job_type CHECK (job_type IN ('SHELL', 'JAVA', 'PYTHON', 'STORED_PROC', 'FILE_WATCHER'))
);

-- Job Parameters Table
CREATE TABLE job_parameters (
    parameter_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    parameter_name NVARCHAR(100) NOT NULL,
    parameter_value NVARCHAR(MAX),
    parameter_type NVARCHAR(50) DEFAULT 'STRING', -- STRING, INTEGER, DATE, BOOLEAN, JSON
    is_encrypted BIT DEFAULT 0,
    is_required BIT DEFAULT 1,
    default_value NVARCHAR(MAX),
    validation_regex NVARCHAR(500),
    description NVARCHAR(500),
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE,
    UNIQUE(job_id, parameter_name)
);

-- Job Schedule Table
CREATE TABLE job_schedules (
    schedule_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    schedule_name NVARCHAR(255) NOT NULL,
    schedule_type NVARCHAR(50) NOT NULL, -- CRON, FIXED_DELAY, FIXED_RATE, ONE_TIME, EVENT_BASED
    cron_expression NVARCHAR(100), -- For CRON type
    fixed_delay_seconds INT, -- For FIXED_DELAY type
    fixed_rate_seconds INT, -- For FIXED_RATE type
    one_time_execution DATETIME2, -- For ONE_TIME type
    event_name NVARCHAR(255), -- For EVENT_BASED type
    start_date DATETIME2,
    end_date DATETIME2,
    time_zone NVARCHAR(50) DEFAULT 'UTC',
    is_active BIT DEFAULT 1,
    next_run_time DATETIME2,
    last_run_time DATETIME2,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL,
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE,
    CONSTRAINT chk_schedule_type CHECK (schedule_type IN ('CRON', 'FIXED_DELAY', 'FIXED_RATE', 'ONE_TIME', 'EVENT_BASED'))
);

-- Job Dependencies Table
CREATE TABLE job_dependencies (
    dependency_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    dependent_job_id BIGINT NOT NULL,
    dependency_type NVARCHAR(50) DEFAULT 'SUCCESS', -- SUCCESS, FAILURE, COMPLETION, CONDITIONAL
    dependency_condition NVARCHAR(MAX), -- For CONDITIONAL type (JSON expression)
    wait_timeout_minutes INT DEFAULT 180,
    is_active BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id),
    FOREIGN KEY (dependent_job_id) REFERENCES job_definitions(job_id),
    CONSTRAINT chk_dependency_type CHECK (dependency_type IN ('SUCCESS', 'FAILURE', 'COMPLETION', 'CONDITIONAL')),
    UNIQUE(job_id, dependent_job_id)
);

-- Job Execution History Table
CREATE TABLE job_execution_history (
    execution_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_uuid UNIQUEIDENTIFIER DEFAULT NEWID(),
    schedule_id BIGINT,
    trigger_type NVARCHAR(50) NOT NULL, -- SCHEDULED, MANUAL, DEPENDENCY, API, EVENT
    triggered_by NVARCHAR(100),
    status NVARCHAR(50) NOT NULL, -- PENDING, QUEUED, RUNNING, SUCCESS, FAILURE, CANCELLED, TIMEOUT, SKIPPED
    start_time DATETIME2,
    end_time DATETIME2,
    duration_seconds AS DATEDIFF(SECOND, start_time, end_time),
    retry_count INT DEFAULT 0,
    error_message NVARCHAR(MAX),
    error_stack_trace NVARCHAR(MAX),
    warning_message NVARCHAR(MAX),
    host_name NVARCHAR(255),
    process_id INT,
    thread_id NVARCHAR(100),
    input_parameters NVARCHAR(MAX), -- JSON
    output_parameters NVARCHAR(MAX), -- JSON
    log_file_path NVARCHAR(1000),
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id),
    FOREIGN KEY (schedule_id) REFERENCES job_schedules(schedule_id),
    INDEX idx_job_execution_status (job_id, status, start_time),
    INDEX idx_execution_uuid (execution_uuid)
);

-- Job Execution Steps Table (for tracking multi-step jobs)
CREATE TABLE job_execution_steps (
    step_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    step_number INT NOT NULL,
    step_name NVARCHAR(255) NOT NULL,
    status NVARCHAR(50) NOT NULL,
    start_time DATETIME2,
    end_time DATETIME2,
    duration_seconds AS DATEDIFF(SECOND, start_time, end_time),
    error_message NVARCHAR(MAX),
    output NVARCHAR(MAX),
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (execution_id) REFERENCES job_execution_history(execution_id) ON DELETE CASCADE,
    INDEX idx_execution_steps (execution_id, step_number)
);

-- Job Groups Table
CREATE TABLE job_groups (
    group_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_name NVARCHAR(100) NOT NULL UNIQUE,
    parent_group_id BIGINT,
    description NVARCHAR(500),
    owner_team NVARCHAR(100),
    contact_email NVARCHAR(255),
    sla_minutes INT,
    is_active BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL,
    FOREIGN KEY (parent_group_id) REFERENCES job_groups(group_id)
);

-- Alert Configuration Table
CREATE TABLE alert_configurations (
    alert_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT,
    group_id BIGINT,
    alert_type NVARCHAR(50) NOT NULL, -- EMAIL, SMS, TEAMS, SLACK, WEBHOOK
    alert_condition NVARCHAR(50) NOT NULL, -- SUCCESS, FAILURE, TIMEOUT, SLA_BREACH, LONG_RUNNING
    recipient_list NVARCHAR(MAX) NOT NULL, -- Comma-separated or JSON
    email_template NVARCHAR(MAX),
    webhook_url NVARCHAR(1000),
    webhook_headers NVARCHAR(MAX), -- JSON
    webhook_body_template NVARCHAR(MAX),
    is_active BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL,
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES job_groups(group_id) ON DELETE CASCADE,
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('EMAIL', 'SMS', 'TEAMS', 'SLACK', 'WEBHOOK')),
    CONSTRAINT chk_alert_condition CHECK (alert_condition IN ('SUCCESS', 'FAILURE', 'TIMEOUT', 'SLA_BREACH', 'LONG_RUNNING'))
);

-- Alert History Table
CREATE TABLE alert_history (
    alert_history_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    alert_type NVARCHAR(50) NOT NULL,
    alert_status NVARCHAR(50) NOT NULL, -- SENT, FAILED, PENDING
    recipient NVARCHAR(500),
    alert_message NVARCHAR(MAX),
    error_message NVARCHAR(MAX),
    sent_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (alert_id) REFERENCES alert_configurations(alert_id),
    FOREIGN KEY (execution_id) REFERENCES job_execution_history(execution_id),
    INDEX idx_alert_history (execution_id, sent_date)
);

-- Resource Pools Table (for Symphony Grid integration)
CREATE TABLE resource_pools (
    pool_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pool_name NVARCHAR(100) NOT NULL UNIQUE,
    pool_type NVARCHAR(50) NOT NULL, -- CPU, MEMORY, CUSTOM
    max_capacity INT NOT NULL,
    current_usage INT DEFAULT 0,
    reserved_capacity INT DEFAULT 0,
    description NVARCHAR(500),
    is_active BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_date DATETIME2 DEFAULT GETUTCDATE()
);

-- Job Resource Requirements Table
CREATE TABLE job_resource_requirements (
    requirement_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    pool_id BIGINT NOT NULL,
    required_capacity INT NOT NULL,
    priority INT DEFAULT 5,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE,
    FOREIGN KEY (pool_id) REFERENCES resource_pools(pool_id),
    UNIQUE(job_id, pool_id)
);

-- Configuration Audit Table
CREATE TABLE configuration_audit (
    audit_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    table_name NVARCHAR(100) NOT NULL,
    record_id BIGINT NOT NULL,
    operation NVARCHAR(50) NOT NULL, -- INSERT, UPDATE, DELETE
    old_values NVARCHAR(MAX), -- JSON
    new_values NVARCHAR(MAX), -- JSON
    changed_by NVARCHAR(100) NOT NULL,
    change_date DATETIME2 DEFAULT GETUTCDATE(),
    change_reason NVARCHAR(500),
    approved_by NVARCHAR(100),
    INDEX idx_audit_table_record (table_name, record_id, change_date)
);

-- System Configuration Table
CREATE TABLE system_configuration (
    config_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    config_key NVARCHAR(255) NOT NULL UNIQUE,
    config_value NVARCHAR(MAX) NOT NULL,
    config_type NVARCHAR(50) DEFAULT 'STRING',
    description NVARCHAR(500),
    is_encrypted BIT DEFAULT 0,
    is_active BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL
);

-- Holiday Calendar Table
CREATE TABLE holiday_calendar (
    holiday_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    holiday_date DATE NOT NULL,
    holiday_name NVARCHAR(100) NOT NULL,
    country_code NVARCHAR(10) DEFAULT 'US',
    is_bank_holiday BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    UNIQUE(holiday_date, country_code)
);

-- Business Calendar Table
CREATE TABLE business_calendar (
    calendar_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    calendar_name NVARCHAR(100) NOT NULL UNIQUE,
    working_days NVARCHAR(20) DEFAULT 'MON,TUE,WED,THU,FRI',
    business_hours_start TIME DEFAULT '09:00:00',
    business_hours_end TIME DEFAULT '17:00:00',
    time_zone NVARCHAR(50) DEFAULT 'America/New_York',
    include_holidays BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_date DATETIME2 DEFAULT GETUTCDATE()
);

-- Job Calendar Association Table
CREATE TABLE job_calendar_association (
    association_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    calendar_id BIGINT NOT NULL,
    skip_on_holiday BIT DEFAULT 1,
    skip_on_weekend BIT DEFAULT 0,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE,
    FOREIGN KEY (calendar_id) REFERENCES business_calendar(calendar_id),
    UNIQUE(job_id, calendar_id)
);

-- File Watcher Configuration Table
CREATE TABLE file_watcher_config (
    watcher_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    watch_directory NVARCHAR(1000) NOT NULL,
    file_pattern NVARCHAR(255) NOT NULL,
    recursive_watch BIT DEFAULT 0,
    stable_time_seconds INT DEFAULT 5, -- Wait time to ensure file is completely written
    move_after_process BIT DEFAULT 1,
    archive_directory NVARCHAR(1000),
    delete_after_process BIT DEFAULT 0,
    min_file_size_bytes BIGINT,
    max_file_size_bytes BIGINT,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id) ON DELETE CASCADE
);

-- Event Bus Table
CREATE TABLE event_bus (
    event_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    event_name NVARCHAR(255) NOT NULL,
    event_type NVARCHAR(50) NOT NULL,
    event_source NVARCHAR(255),
    event_data NVARCHAR(MAX), -- JSON
    event_timestamp DATETIME2 DEFAULT GETUTCDATE(),
    processed BIT DEFAULT 0,
    processed_date DATETIME2,
    INDEX idx_event_unprocessed (processed, event_timestamp),
    INDEX idx_event_name (event_name, event_timestamp)
);

-- Job Chain Definition Table
CREATE TABLE job_chain_definitions (
    chain_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    chain_name NVARCHAR(255) NOT NULL UNIQUE,
    description NVARCHAR(MAX),
    is_active BIT DEFAULT 1,
    stop_on_failure BIT DEFAULT 1,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    created_by NVARCHAR(100) NOT NULL,
    modified_date DATETIME2 DEFAULT GETUTCDATE(),
    modified_by NVARCHAR(100) NOT NULL
);

-- Job Chain Steps Table
CREATE TABLE job_chain_steps (
    step_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    is_parallel BIT DEFAULT 0, -- Can run in parallel with same step_order
    continue_on_failure BIT DEFAULT 0,
    created_date DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (chain_id) REFERENCES job_chain_definitions(chain_id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES job_definitions(job_id),
    UNIQUE(chain_id, step_order, job_id)
);

-- =============================================
-- Views for Operational Queries
-- =============================================

-- Active Jobs View
CREATE VIEW vw_active_jobs AS
SELECT 
    jd.job_id,
    jd.job_name,
    jd.job_group,
    jd.job_type,
    js.schedule_type,
    js.cron_expression,
    js.next_run_time,
    js.last_run_time,
    jd.priority,
    jd.critical_job
FROM job_definitions jd
LEFT JOIN job_schedules js ON jd.job_id = js.job_id AND js.is_active = 1
WHERE jd.is_active = 1;

-- Job Execution Summary View
CREATE VIEW vw_job_execution_summary AS
SELECT 
    jd.job_name,
    jd.job_group,
    COUNT(jeh.execution_id) as total_executions,
    SUM(CASE WHEN jeh.status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_runs,
    SUM(CASE WHEN jeh.status = 'FAILURE' THEN 1 ELSE 0 END) as failed_runs,
    AVG(jeh.duration_seconds) as avg_duration_seconds,
    MAX(jeh.duration_seconds) as max_duration_seconds,
    MIN(jeh.duration_seconds) as min_duration_seconds,
    MAX(jeh.start_time) as last_run_time
FROM job_definitions jd
LEFT JOIN job_execution_history jeh ON jd.job_id = jeh.job_id
GROUP BY jd.job_name, jd.job_group;

-- Currently Running Jobs View
CREATE VIEW vw_running_jobs AS
SELECT 
    jeh.execution_id,
    jd.job_name,
    jd.job_group,
    jeh.start_time,
    DATEDIFF(MINUTE, jeh.start_time, GETUTCDATE()) as running_minutes,
    jeh.triggered_by,
    jeh.host_name,
    jd.timeout_minutes
FROM job_execution_history jeh
INNER JOIN job_definitions jd ON jeh.job_id = jd.job_id
WHERE jeh.status = 'RUNNING';

-- =============================================
-- Stored Procedures
-- =============================================

-- Get Next Jobs to Execute
CREATE PROCEDURE sp_get_next_jobs_to_execute
AS
BEGIN
    SET NOCOUNT ON;
    
    WITH NextJobs AS (
        SELECT 
            js.schedule_id,
            js.job_id,
            js.next_run_time,
            jd.priority,
            ROW_NUMBER() OVER (ORDER BY jd.priority DESC, js.next_run_time) as rn
        FROM job_schedules js
        INNER JOIN job_definitions jd ON js.job_id = jd.job_id
        WHERE js.is_active = 1 
            AND jd.is_active = 1
            AND js.next_run_time <= GETUTCDATE()
            AND NOT EXISTS (
                SELECT 1 FROM job_execution_history jeh 
                WHERE jeh.job_id = js.job_id 
                    AND jeh.status IN ('PENDING', 'QUEUED', 'RUNNING')
            )
    )
    SELECT * FROM NextJobs WHERE rn <= 100; -- Limit batch size
END;
GO

-- Check Job Dependencies
CREATE PROCEDURE sp_check_job_dependencies
    @job_id BIGINT,
    @can_execute BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET @can_execute = 1;
    
    IF EXISTS (
        SELECT 1 
        FROM job_dependencies jd
        INNER JOIN job_execution_history jeh ON jd.dependent_job_id = jeh.job_id
        WHERE jd.job_id = @job_id 
            AND jd.is_active = 1
            AND (
                (jd.dependency_type = 'SUCCESS' AND jeh.status != 'SUCCESS')
                OR (jd.dependency_type = 'FAILURE' AND jeh.status != 'FAILURE')
                OR (jd.dependency_type = 'COMPLETION' AND jeh.status IN ('PENDING', 'QUEUED', 'RUNNING'))
            )
            AND jeh.start_time = (
                SELECT MAX(start_time) 
                FROM job_execution_history 
                WHERE job_id = jd.dependent_job_id
            )
    )
    BEGIN
        SET @can_execute = 0;
    END
END;
GO

-- =============================================
-- Indexes for Performance
-- =============================================

CREATE INDEX idx_job_definitions_group ON job_definitions(job_group, is_active);
CREATE INDEX idx_job_schedules_next_run ON job_schedules(next_run_time, is_active);
CREATE INDEX idx_job_execution_history_job_time ON job_execution_history(job_id, start_time DESC);
CREATE INDEX idx_job_dependencies_active ON job_dependencies(job_id, is_active);
CREATE INDEX idx_alert_configurations_job ON alert_configurations(job_id, is_active);

-- =============================================
-- Initial System Configuration
-- =============================================

INSERT INTO system_configuration (config_key, config_value, config_type, description, created_by, modified_by)
VALUES 
    ('max.concurrent.jobs', '50', 'INTEGER', 'Maximum number of concurrent job executions', 'SYSTEM', 'SYSTEM'),
    ('scheduler.poll.interval.seconds', '10', 'INTEGER', 'Scheduler polling interval in seconds', 'SYSTEM', 'SYSTEM'),
    ('job.queue.capacity', '1000', 'INTEGER', 'Maximum job queue capacity', 'SYSTEM', 'SYSTEM'),
    ('alert.retry.max.attempts', '3', 'INTEGER', 'Maximum alert retry attempts', 'SYSTEM', 'SYSTEM'),
    ('file.watcher.poll.interval.seconds', '30', 'INTEGER', 'File watcher polling interval', 'SYSTEM', 'SYSTEM'),
    ('execution.history.retention.days', '90', 'INTEGER', 'Execution history retention period', 'SYSTEM', 'SYSTEM'),
    ('symphony.grid.endpoint', 'http://symphony-grid:8080', 'STRING', 'Symphony Grid API endpoint', 'SYSTEM', 'SYSTEM'),
    ('smtp.host', 'smtp.bank.com', 'STRING', 'SMTP server for email alerts', 'SYSTEM', 'SYSTEM'),
    ('smtp.port', '587', 'INTEGER', 'SMTP server port', 'SYSTEM', 'SYSTEM');

-- =============================================
-- Triggers for Audit Logging
-- =============================================

CREATE TRIGGER trg_job_definitions_audit
ON job_definitions
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    
    IF EXISTS (SELECT * FROM inserted) AND EXISTS (SELECT * FROM deleted)
    BEGIN
        -- UPDATE
        INSERT INTO configuration_audit (table_name, record_id, operation, old_values, new_values, changed_by, change_date)
        SELECT 
            'job_definitions',
            i.job_id,
            'UPDATE',
            (SELECT * FROM deleted WHERE job_id = i.job_id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            (SELECT * FROM inserted WHERE job_id = i.job_id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            i.modified_by,
            GETUTCDATE()
        FROM inserted i;
    END
    ELSE IF EXISTS (SELECT * FROM inserted)
    BEGIN
        -- INSERT
        INSERT INTO configuration_audit (table_name, record_id, operation, new_values, changed_by, change_date)
        SELECT 
            'job_definitions',
            job_id,
            'INSERT',
            (SELECT * FROM inserted WHERE job_id = inserted.job_id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            created_by,
            GETUTCDATE()
        FROM inserted;
    END
    ELSE IF EXISTS (SELECT * FROM deleted)
    BEGIN
        -- DELETE
        INSERT INTO configuration_audit (table_name, record_id, operation, old_values, changed_by, change_date)
        SELECT 
            'job_definitions',
            job_id,
            'DELETE',
            (SELECT * FROM deleted WHERE job_id = deleted.job_id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            'SYSTEM',
            GETUTCDATE()
        FROM deleted;
    END
END;
GO