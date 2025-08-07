-- =============================================
-- Sample Job Configurations for Banking Environment
-- =============================================

-- Sample Job Groups
INSERT INTO job_groups (group_name, description, owner_team, contact_email, sla_minutes, created_by, modified_by)
VALUES 
    ('CORE_BANKING', 'Core banking batch processes', 'Core Banking Team', 'corebanking@bank.com', 240, 'ADMIN', 'ADMIN'),
    ('RISK_MANAGEMENT', 'Risk and compliance jobs', 'Risk Team', 'risk@bank.com', 180, 'ADMIN', 'ADMIN'),
    ('DATA_WAREHOUSE', 'ETL and data warehouse jobs', 'Data Team', 'data@bank.com', 360, 'ADMIN', 'ADMIN'),
    ('REPORTING', 'Report generation jobs', 'Reporting Team', 'reports@bank.com', 120, 'ADMIN', 'ADMIN'),
    ('RECONCILIATION', 'Account reconciliation jobs', 'Operations Team', 'ops@bank.com', 90, 'ADMIN', 'ADMIN');

-- =============================================
-- Sample Job 1: End of Day Balance Calculation
-- =============================================
INSERT INTO job_definitions (
    job_name, job_group, job_type, stored_proc_name, description,
    priority, max_retry_count, retry_interval_seconds, timeout_minutes,
    alert_on_failure, critical_job, created_by, modified_by
) VALUES (
    'EOD_BALANCE_CALC', 'CORE_BANKING', 'STORED_PROC', 'sp_calculate_eod_balances',
    'Calculate end of day balances for all accounts',
    9, 2, 600, 120, 1, 1, 'ADMIN', 'ADMIN'
);

-- Parameters for EOD Balance job
INSERT INTO job_parameters (job_id, parameter_name, parameter_value, parameter_type, is_required, description)
VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     'business_date', 'CURRENT_DATE', 'DATE', 1, 'Business date for calculation'),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     'account_type', 'ALL', 'STRING', 0, 'Account type filter (ALL, SAVINGS, CHECKING, LOAN)');

-- Schedule for EOD Balance job
INSERT INTO job_schedules (
    job_id, schedule_name, schedule_type, cron_expression,
    time_zone, is_active, created_by, modified_by
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
    'EOD_DAILY_SCHEDULE', 'CRON', '0 0 20 * * MON-FRI',
    'America/New_York', 1, 'ADMIN', 'ADMIN'
);

-- =============================================
-- Sample Job 2: Transaction Settlement
-- =============================================
INSERT INTO job_definitions (
    job_name, job_group, job_type, script_path, description,
    priority, max_retry_count, retry_interval_seconds, timeout_minutes,
    alert_on_failure, critical_job, created_by, modified_by
) VALUES (
    'TRANSACTION_SETTLEMENT', 'CORE_BANKING', 'SHELL', '/opt/batch/scripts/settle_transactions.sh',
    'Process and settle pending transactions',
    10, 3, 300, 90, 1, 1, 'ADMIN', 'ADMIN'
);

-- Parameters for Transaction Settlement
INSERT INTO job_parameters (job_id, parameter_name, parameter_value, parameter_type, is_required, description)
VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
     'settlement_type', 'NORMAL', 'STRING', 1, 'Settlement type (NORMAL, EXPEDITED, MANUAL)'),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
     'batch_size', '1000', 'INTEGER', 0, 'Number of transactions per batch');

-- Schedule for Transaction Settlement
INSERT INTO job_schedules (
    job_id, schedule_name, schedule_type, fixed_rate_seconds,
    time_zone, is_active, created_by, modified_by
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
    'INTRADAY_SETTLEMENT', 'FIXED_RATE', 3600,  -- Every hour
    'America/New_York', 1, 'ADMIN', 'ADMIN'
);

-- =============================================
-- Sample Job 3: Risk Report Generation
-- =============================================
INSERT INTO job_definitions (
    job_name, job_group, job_type, job_class_name, description,
    priority, max_retry_count, retry_interval_seconds, timeout_minutes,
    alert_on_failure, alert_on_success, created_by, modified_by
) VALUES (
    'DAILY_RISK_REPORT', 'RISK_MANAGEMENT', 'JAVA', 'com.bank.batch.jobs.RiskReportJob',
    'Generate daily risk assessment report',
    7, 2, 900, 180, 1, 1, 'ADMIN', 'ADMIN'
);

-- Schedule for Risk Report
INSERT INTO job_schedules (
    job_id, schedule_name, schedule_type, cron_expression,
    time_zone, is_active, created_by, modified_by
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'DAILY_RISK_REPORT'),
    'DAILY_RISK_SCHEDULE', 'CRON', '0 30 6 * * *',  -- 6:30 AM daily
    'America/New_York', 1, 'ADMIN', 'ADMIN'
);

-- =============================================
-- Sample Job 4: File Watcher for ACH Files
-- =============================================
INSERT INTO job_definitions (
    job_name, job_group, job_type, script_path, description,
    priority, max_retry_count, retry_interval_seconds, timeout_minutes,
    alert_on_failure, created_by, modified_by
) VALUES (
    'ACH_FILE_PROCESSOR', 'CORE_BANKING', 'FILE_WATCHER', '/opt/batch/scripts/process_ach.py',
    'Process incoming ACH files from Federal Reserve',
    10, 1, 600, 60, 1, 'ADMIN', 'ADMIN'
);

-- File watcher configuration
INSERT INTO file_watcher_config (
    job_id, watch_directory, file_pattern, recursive_watch,
    stable_time_seconds, move_after_process, archive_directory,
    min_file_size_bytes, max_file_size_bytes
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'ACH_FILE_PROCESSOR'),
    '/data/incoming/ach', '*.ach', 0, 10, 1, '/data/archive/ach',
    1024, 104857600  -- Min 1KB, Max 100MB
);

-- =============================================
-- Sample Job 5: Data Warehouse ETL
-- =============================================
INSERT INTO job_definitions (
    job_name, job_group, job_type, job_class_name, description,
    priority, max_retry_count, retry_interval_seconds, timeout_minutes,
    alert_on_failure, created_by, modified_by
) VALUES (
    'DW_CUSTOMER_ETL', 'DATA_WAREHOUSE', 'PYTHON', 'etl_customer_data.py',
    'Extract, transform and load customer data to warehouse',
    6, 3, 1200, 240, 1, 'ADMIN', 'ADMIN'
);

-- Parameters for ETL job
INSERT INTO job_parameters (job_id, parameter_name, parameter_value, parameter_type, is_required, description)
VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'DW_CUSTOMER_ETL'),
     'source_database', 'CORE_BANKING_PROD', 'STRING', 1, 'Source database connection'),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'DW_CUSTOMER_ETL'),
     'target_schema', 'DW_CUSTOMER', 'STRING', 1, 'Target warehouse schema'),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'DW_CUSTOMER_ETL'),
     'incremental_mode', 'true', 'BOOLEAN', 0, 'Use incremental load');

-- Schedule for ETL job
INSERT INTO job_schedules (
    job_id, schedule_name, schedule_type, cron_expression,
    time_zone, is_active, created_by, modified_by
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'DW_CUSTOMER_ETL'),
    'NIGHTLY_ETL', 'CRON', '0 0 2 * * *',  -- 2 AM daily
    'America/New_York', 1, 'ADMIN', 'ADMIN'
);

-- =============================================
-- Sample Job Chain: Month End Processing
-- =============================================
INSERT INTO job_chain_definitions (chain_name, description, stop_on_failure, created_by, modified_by)
VALUES ('MONTH_END_PROCESSING', 'Complete month end processing workflow', 1, 'ADMIN', 'ADMIN');

-- Create individual jobs for the chain
INSERT INTO job_definitions (
    job_name, job_group, job_type, stored_proc_name, description,
    priority, timeout_minutes, created_by, modified_by
) VALUES 
    ('MONTH_END_INTEREST_CALC', 'CORE_BANKING', 'STORED_PROC', 'sp_calculate_monthly_interest',
     'Calculate monthly interest for all accounts', 9, 180, 'ADMIN', 'ADMIN'),
    ('MONTH_END_FEES', 'CORE_BANKING', 'STORED_PROC', 'sp_apply_monthly_fees',
     'Apply monthly maintenance fees', 8, 120, 'ADMIN', 'ADMIN'),
    ('MONTH_END_STATEMENTS', 'REPORTING', 'JAVA', 'com.bank.batch.jobs.StatementGenerationJob',
     'Generate monthly statements', 7, 240, 'ADMIN', 'ADMIN'),
    ('MONTH_END_GL_CLOSE', 'CORE_BANKING', 'STORED_PROC', 'sp_close_general_ledger',
     'Close general ledger for the month', 10, 60, 'ADMIN', 'ADMIN');

-- Add jobs to the chain
INSERT INTO job_chain_steps (chain_id, job_id, step_order, is_parallel)
VALUES 
    ((SELECT chain_id FROM job_chain_definitions WHERE chain_name = 'MONTH_END_PROCESSING'),
     (SELECT job_id FROM job_definitions WHERE job_name = 'MONTH_END_INTEREST_CALC'), 1, 0),
    ((SELECT chain_id FROM job_chain_definitions WHERE chain_name = 'MONTH_END_PROCESSING'),
     (SELECT job_id FROM job_definitions WHERE job_name = 'MONTH_END_FEES'), 2, 0),
    ((SELECT chain_id FROM job_chain_definitions WHERE chain_name = 'MONTH_END_PROCESSING'),
     (SELECT job_id FROM job_definitions WHERE job_name = 'MONTH_END_STATEMENTS'), 3, 0),
    ((SELECT chain_id FROM job_chain_definitions WHERE chain_name = 'MONTH_END_PROCESSING'),
     (SELECT job_id FROM job_definitions WHERE job_name = 'MONTH_END_GL_CLOSE'), 4, 0);

-- =============================================
-- Job Dependencies
-- =============================================

-- EOD Balance must complete before Risk Report
INSERT INTO job_dependencies (job_id, dependent_job_id, dependency_type, created_by)
VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'DAILY_RISK_REPORT'),
    (SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
    'SUCCESS', 'ADMIN'
);

-- Transaction Settlement must complete before EOD Balance
INSERT INTO job_dependencies (job_id, dependent_job_id, dependency_type, created_by)
VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
    (SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
    'SUCCESS', 'ADMIN'
);

-- =============================================
-- Alert Configurations
-- =============================================

-- Email alerts for critical jobs
INSERT INTO alert_configurations (
    job_id, alert_type, alert_condition, recipient_list,
    email_template, is_active, created_by, modified_by
) VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     'EMAIL', 'FAILURE', 'corebanking@bank.com,ops@bank.com',
     'CRITICAL: EOD Balance Calculation failed for {job_name}. Error: {error_message}',
     1, 'ADMIN', 'ADMIN'),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
     'EMAIL', 'FAILURE', 'corebanking@bank.com,ops@bank.com,management@bank.com',
     'CRITICAL: Transaction Settlement failed. Immediate action required. Error: {error_message}',
     1, 'ADMIN', 'ADMIN');

-- Teams webhook for risk reports
INSERT INTO alert_configurations (
    job_id, alert_type, alert_condition, webhook_url,
    webhook_body_template, is_active, created_by, modified_by
) VALUES (
    (SELECT job_id FROM job_definitions WHERE job_name = 'DAILY_RISK_REPORT'),
    'WEBHOOK', 'SUCCESS',
    'https://bank.webhook.office.com/webhookb2/team-risk',
    '{"text": "Daily Risk Report completed successfully at {end_time}. Report available at: {output_link}"}',
    1, 'ADMIN', 'ADMIN'
);

-- =============================================
-- Resource Pool Configuration
-- =============================================

INSERT INTO resource_pools (pool_name, pool_type, max_capacity, description)
VALUES 
    ('HIGH_PRIORITY_CPU', 'CPU', 20, 'CPU pool for high priority jobs'),
    ('NORMAL_CPU', 'CPU', 50, 'CPU pool for normal priority jobs'),
    ('LARGE_MEMORY', 'MEMORY', 32768, 'Memory pool for large jobs (in MB)'),
    ('NORMAL_MEMORY', 'MEMORY', 65536, 'Memory pool for normal jobs (in MB)');

-- Assign resource requirements to critical jobs
INSERT INTO job_resource_requirements (job_id, pool_id, required_capacity, priority)
VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     (SELECT pool_id FROM resource_pools WHERE pool_name = 'HIGH_PRIORITY_CPU'), 4, 9),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     (SELECT pool_id FROM resource_pools WHERE pool_name = 'LARGE_MEMORY'), 8192, 9),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
     (SELECT pool_id FROM resource_pools WHERE pool_name = 'HIGH_PRIORITY_CPU'), 2, 10),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'DW_CUSTOMER_ETL'),
     (SELECT pool_id FROM resource_pools WHERE pool_name = 'LARGE_MEMORY'), 16384, 6);

-- =============================================
-- Business Calendar Configuration
-- =============================================

INSERT INTO business_calendar (
    calendar_name, working_days, business_hours_start, business_hours_end,
    time_zone, include_holidays
) VALUES (
    'BANK_CALENDAR', 'MON,TUE,WED,THU,FRI', '08:00:00', '18:00:00',
    'America/New_York', 1
);

-- Associate critical jobs with business calendar
INSERT INTO job_calendar_association (job_id, calendar_id, skip_on_holiday, skip_on_weekend)
VALUES 
    ((SELECT job_id FROM job_definitions WHERE job_name = 'EOD_BALANCE_CALC'),
     (SELECT calendar_id FROM business_calendar WHERE calendar_name = 'BANK_CALENDAR'), 0, 1),
    ((SELECT job_id FROM job_definitions WHERE job_name = 'TRANSACTION_SETTLEMENT'),
     (SELECT calendar_id FROM business_calendar WHERE calendar_name = 'BANK_CALENDAR'), 1, 1);

-- =============================================
-- Sample Holiday Calendar
-- =============================================

INSERT INTO holiday_calendar (holiday_date, holiday_name, country_code, is_bank_holiday)
VALUES 
    ('2024-01-01', 'New Year''s Day', 'US', 1),
    ('2024-01-15', 'Martin Luther King Jr. Day', 'US', 1),
    ('2024-02-19', 'Presidents Day', 'US', 1),
    ('2024-05-27', 'Memorial Day', 'US', 1),
    ('2024-07-04', 'Independence Day', 'US', 1),
    ('2024-09-02', 'Labor Day', 'US', 1),
    ('2024-10-14', 'Columbus Day', 'US', 1),
    ('2024-11-11', 'Veterans Day', 'US', 1),
    ('2024-11-28', 'Thanksgiving Day', 'US', 1),
    ('2024-12-25', 'Christmas Day', 'US', 1);