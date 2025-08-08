import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JobService } from '../../services/job.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard">
      <h2>Dashboard</h2>
      
      <div class="stats-grid">
        <div class="stat-card">
          <h3>Total Jobs</h3>
          <p class="stat-value">{{ stats.totalJobs }}</p>
        </div>
        
        <div class="stat-card">
          <h3>Running Jobs</h3>
          <p class="stat-value">{{ stats.runningJobs }}</p>
        </div>
        
        <div class="stat-card">
          <h3>Failed Jobs</h3>
          <p class="stat-value">{{ stats.failedJobs }}</p>
        </div>
        
        <div class="stat-card">
          <h3>Success Rate</h3>
          <p class="stat-value">{{ stats.successRate }}%</p>
        </div>
      </div>
      
      <div class="recent-section">
        <h3>Recent Executions</h3>
        <div class="execution-list">
          <div *ngIf="recentExecutions.length === 0" class="no-data">
            <p>No recent executions to display</p>
          </div>
          
          <div *ngIf="recentExecutions.length > 0" class="execution-table">
            <table>
              <thead>
                <tr>
                  <th>Job Name</th>
                  <th>Execution ID</th>
                  <th>Status</th>
                  <th>Start Time</th>
                  <th>Duration</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let execution of recentExecutions">
                  <td class="job-name">{{ execution.jobName }}</td>
                  <td class="exec-id">#{{ execution.executionId }}</td>
                  <td>
                    <span class="status-badge" [ngClass]="'status-' + execution.status.toLowerCase()">
                      {{ execution.status }}
                    </span>
                  </td>
                  <td class="time">{{ execution.startTime }}</td>
                  <td class="duration">{{ execution.duration }}</td>
                  <td>
                    <button class="btn-action" (click)="viewDetails(execution)">View</button>
                    <button *ngIf="execution.status === 'RUNNING'" class="btn-action btn-stop" (click)="stopExecution(execution)">Stop</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          
          <div class="load-more" *ngIf="recentExecutions.length > 0">
            <button class="btn-load-more" (click)="loadMoreExecutions()">Load More</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard {
      padding: 1rem;
    }
    
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1.5rem;
      margin: 2rem 0;
    }
    
    .stat-card {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      transition: transform 0.2s;
    }
    
    .stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.15);
    }
    
    .stat-card h3 {
      color: #666;
      font-size: 0.9rem;
      margin-bottom: 0.5rem;
    }
    
    .stat-value {
      font-size: 2rem;
      font-weight: bold;
      color: #2c3e50;
    }
    
    .recent-section {
      margin-top: 2rem;
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .recent-section h3 {
      margin-bottom: 1.5rem;
      color: #2c3e50;
    }
    
    .no-data {
      text-align: center;
      padding: 2rem;
      color: #999;
    }
    
    .execution-table {
      overflow-x: auto;
    }
    
    .execution-table table {
      width: 100%;
      border-collapse: collapse;
    }
    
    .execution-table th {
      background-color: #f8f9fa;
      padding: 0.75rem;
      text-align: left;
      font-weight: 600;
      color: #555;
      border-bottom: 2px solid #dee2e6;
    }
    
    .execution-table td {
      padding: 0.75rem;
      border-bottom: 1px solid #e9ecef;
    }
    
    .execution-table tr:hover {
      background-color: #f8f9fa;
    }
    
    .job-name {
      font-weight: 500;
      color: #2c3e50;
    }
    
    .exec-id {
      font-family: monospace;
      color: #666;
    }
    
    .status-badge {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      border-radius: 12px;
      font-size: 0.85rem;
      font-weight: 500;
      text-transform: uppercase;
    }
    
    .status-success {
      background-color: #d4edda;
      color: #155724;
    }
    
    .status-running {
      background-color: #cce5ff;
      color: #004085;
    }
    
    .status-failed {
      background-color: #f8d7da;
      color: #721c24;
    }
    
    .status-pending {
      background-color: #fff3cd;
      color: #856404;
    }
    
    .time {
      color: #666;
      font-size: 0.9rem;
    }
    
    .duration {
      color: #666;
      font-size: 0.9rem;
    }
    
    .btn-action {
      padding: 0.25rem 0.75rem;
      margin-right: 0.5rem;
      border: 1px solid #dee2e6;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.85rem;
      transition: all 0.2s;
    }
    
    .btn-action:hover {
      background-color: #f8f9fa;
      border-color: #adb5bd;
    }
    
    .btn-stop {
      color: #dc3545;
      border-color: #dc3545;
    }
    
    .btn-stop:hover {
      background-color: #dc3545;
      color: white;
    }
    
    .load-more {
      text-align: center;
      margin-top: 1rem;
    }
    
    .btn-load-more {
      padding: 0.5rem 1.5rem;
      border: 1px solid #3498db;
      background: white;
      color: #3498db;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.2s;
    }
    
    .btn-load-more:hover {
      background-color: #3498db;
      color: white;
    }
  `]
})
export class DashboardComponent implements OnInit {
  stats = {
    totalJobs: 0,
    runningJobs: 0,
    failedJobs: 0,
    successRate: 0
  };
  
  recentExecutions: any[] = [];

  constructor(private jobService: JobService) {}

  ngOnInit() {
    this.loadStats();
    this.loadRecentExecutions();
    
    // Simulate real-time updates
    setInterval(() => {
      this.updateExecutionStatuses();
    }, 5000);
  }

  loadStats() {
    // Simulate loading stats from API
    this.stats = {
      totalJobs: 8,
      runningJobs: 2,
      failedJobs: 1,
      successRate: 87
    };
  }
  
  loadRecentExecutions() {
    // Simulate loading recent executions
    this.recentExecutions = [
      {
        jobName: 'Daily Report',
        executionId: 10234,
        status: 'SUCCESS',
        startTime: '2024-01-08 18:00:00',
        duration: '2m 34s'
      },
      {
        jobName: 'Data Sync',
        executionId: 10233,
        status: 'RUNNING',
        startTime: '2024-01-08 17:30:00',
        duration: '32m 15s'
      },
      {
        jobName: 'Backup Job',
        executionId: 10232,
        status: 'SUCCESS',
        startTime: '2024-01-08 17:00:00',
        duration: '45m 12s'
      },
      {
        jobName: 'Email Notifications',
        executionId: 10231,
        status: 'FAILED',
        startTime: '2024-01-08 16:45:00',
        duration: '0m 23s'
      },
      {
        jobName: 'Cleanup Logs',
        executionId: 10230,
        status: 'SUCCESS',
        startTime: '2024-01-08 16:00:00',
        duration: '5m 18s'
      },
      {
        jobName: 'Health Check',
        executionId: 10229,
        status: 'RUNNING',
        startTime: '2024-01-08 15:55:00',
        duration: '2h 23m'
      },
      {
        jobName: 'ETL Extract',
        executionId: 10228,
        status: 'SUCCESS',
        startTime: '2024-01-08 15:00:00',
        duration: '18m 45s'
      },
      {
        jobName: 'ETL Transform',
        executionId: 10227,
        status: 'PENDING',
        startTime: '2024-01-08 14:30:00',
        duration: '-'
      }
    ];
  }
  
  updateExecutionStatuses() {
    // Simulate status updates for running jobs
    this.recentExecutions.forEach(exec => {
      if (exec.status === 'RUNNING') {
        // Update duration for running jobs
        const startTime = new Date(exec.startTime);
        const now = new Date();
        const diff = Math.floor((now.getTime() - startTime.getTime()) / 1000);
        const hours = Math.floor(diff / 3600);
        const minutes = Math.floor((diff % 3600) / 60);
        const seconds = diff % 60;
        
        if (hours > 0) {
          exec.duration = `${hours}h ${minutes}m`;
        } else if (minutes > 0) {
          exec.duration = `${minutes}m ${seconds}s`;
        } else {
          exec.duration = `${seconds}s`;
        }
        
        // Randomly complete some running jobs
        if (Math.random() > 0.9) {
          exec.status = Math.random() > 0.2 ? 'SUCCESS' : 'FAILED';
        }
      }
    });
  }
  
  viewDetails(execution: any) {
    console.log('Viewing details for execution:', execution);
    alert(`Execution Details:\nJob: ${execution.jobName}\nID: ${execution.executionId}\nStatus: ${execution.status}\nStarted: ${execution.startTime}\nDuration: ${execution.duration}`);
  }
  
  stopExecution(execution: any) {
    console.log('Stopping execution:', execution);
    execution.status = 'FAILED';
    alert(`Execution ${execution.executionId} has been stopped.`);
  }
  
  loadMoreExecutions() {
    // Simulate loading more executions
    const moreExecutions = [
      {
        jobName: 'ETL Load',
        executionId: 10226,
        status: 'SUCCESS',
        startTime: '2024-01-08 14:00:00',
        duration: '22m 10s'
      },
      {
        jobName: 'Daily Report',
        executionId: 10225,
        status: 'SUCCESS',
        startTime: '2024-01-08 13:00:00',
        duration: '2m 45s'
      }
    ];
    
    this.recentExecutions = [...this.recentExecutions, ...moreExecutions];
  }
}