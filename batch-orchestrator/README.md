# Batch Job Orchestrator System

A comprehensive batch job orchestration system designed as a modern replacement for Autosys in banking environments. Built with Spring Boot and H2 database for easy local demonstration.

## Features

- **Job Management**: Create, update, delete, and schedule batch jobs
- **Dependency Management**: Define job dependencies and automatic triggering
- **Scheduling**: Cron-based scheduling with Quartz
- **Execution Engine**: Robust job execution with retry mechanisms
- **Real-time Monitoring**: WebSocket-based real-time job status updates
- **Alerting System**: Automatic alerts for job failures and timeouts
- **REST API**: Comprehensive API for job management and monitoring
- **H2 Database**: In-memory database for easy local setup

## Prerequisites

- Java 17 or higher
- Gradle 7.x or higher

## Quick Start

### 1. Build the Application

```bash
cd batch-orchestrator
./gradlew clean build
```

If you don't have Gradle installed, use the Gradle wrapper:
```bash
# On Unix/Mac/Linux:
./gradlew clean build

# On Windows:
gradlew.bat clean build
```

### 2. Run the Application

```bash
./gradlew bootRun
```

Or run the JAR directly:
```bash
java -jar build/libs/batch-orchestrator-1.0.0.jar
```

### 3. Access the Application

- **REST API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **H2 Console**: http://localhost:8080/api/h2-console
  - JDBC URL: `jdbc:h2:mem:batchdb`
  - Username: `sa`
  - Password: (leave empty)

## Sample Jobs

The application comes pre-loaded with sample jobs:

1. **daily-report**: Generates daily reports (runs at 2 AM daily)
2. **data-backup**: Backs up database (runs at 3 AM daily)
3. **email-notifications**: Sends notifications (every 30 minutes)
4. **cleanup-logs**: Cleans old logs (Sundays at 4 AM)
5. **health-check**: System health check (every 15 minutes)
6. **ETL Pipeline**: Extract → Transform → Load (with dependencies)

## API Endpoints

### Job Management

- `GET /api/jobs` - List all jobs
- `GET /api/jobs/{jobId}` - Get job by ID
- `POST /api/jobs` - Create new job
- `PUT /api/jobs/{jobId}` - Update job
- `DELETE /api/jobs/{jobId}` - Delete job
- `POST /api/jobs/{jobName}/execute` - Execute job manually

### Execution Management

- `GET /api/executions/{executionId}` - Get execution status
- `POST /api/executions/{executionId}/cancel` - Cancel execution
- `POST /api/executions/{executionId}/retry` - Retry failed execution
- `GET /api/executions/running` - List running executions

### Monitoring

- `GET /api/monitoring/metrics` - System metrics
- `GET /api/monitoring/health` - Health status
- `GET /api/monitoring/alerts` - Open alerts
- `PUT /api/monitoring/alerts/{alertId}/acknowledge` - Acknowledge alert
- `PUT /api/monitoring/alerts/{alertId}/resolve` - Resolve alert

## Creating a Job via API

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "my-custom-job",
    "description": "My custom job",
    "jobType": "SHELL",
    "command": "echo \"Hello from custom job!\"",
    "schedule": "0 */5 * * * ?",
    "active": true,
    "maxRetries": 3,
    "timeoutMinutes": 30,
    "priority": 5,
    "parameters": {
      "env": "dev"
    },
    "createdBy": "admin"
  }'
```

## Executing a Job Manually

```bash
curl -X POST http://localhost:8080/api/jobs/my-custom-job/execute \
  -H "Content-Type: application/json" \
  -d '{
    "param1": "value1",
    "param2": "value2"
  }'
```

## WebSocket Connection for Real-time Updates

Connect to WebSocket endpoint: `ws://localhost:8080/api/ws-endpoint`

Subscribe to topics:
- `/topic/job-status` - Real-time job status updates
- `/topic/alerts` - Real-time alerts

## Architecture

The system follows a microservices-ready architecture with:

- **Controller Layer**: REST API endpoints
- **Service Layer**: Business logic (Orchestrator, Configuration, Monitoring)
- **Repository Layer**: Data access using Spring Data JPA
- **Execution Engine**: Job execution with process management
- **Scheduler**: Quartz-based scheduling
- **WebSocket**: Real-time communication

## Configuration

Edit `src/main/resources/application.yml` to customize:

- Server port
- Database settings
- Job execution parameters
- Monitoring thresholds
- Scheduling configuration

## Testing the System

1. **View all jobs**:
   ```bash
   curl http://localhost:8080/api/jobs
   ```

2. **Execute a job**:
   ```bash
   curl -X POST http://localhost:8080/api/jobs/health-check/execute
   ```

3. **Check metrics**:
   ```bash
   curl http://localhost:8080/api/monitoring/metrics
   ```

4. **View running jobs**:
   ```bash
   curl http://localhost:8080/api/executions/running
   ```

## Production Considerations

For production deployment:

1. Replace H2 with a production database (PostgreSQL, MySQL, SQL Server)
2. Implement proper authentication and authorization
3. Configure external monitoring tools
4. Set up proper logging and log aggregation
5. Implement backup and disaster recovery
6. Configure load balancing and high availability
7. Set up proper secret management
8. Implement network security and firewalls

## Troubleshooting

- **Port already in use**: Change the port in `application.yml`
- **Build fails**: Ensure Java 17 is installed and JAVA_HOME is set
- **Jobs not executing**: Check the H2 console for job status
- **WebSocket connection fails**: Ensure no proxy is blocking WebSocket connections

## License

This is a demonstration project for educational purposes.