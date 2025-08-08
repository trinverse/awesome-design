import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-executions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="executions">
      <div class="header">
        <h2>Execution History</h2>
        <div class="header-actions">
          <button class="btn-refresh" (click)="refreshExecutions()">
            <span class="refresh-icon">↻</span> Refresh
          </button>
        </div>
      </div>
      
      <div class="filters">
        <div class="filter-group">
          <label>Status:</label>
          <select [(ngModel)]="filters.status" (change)="applyFilters()">
            <option value="">All</option>
            <option value="SUCCESS">Success</option>
            <option value="RUNNING">Running</option>
            <option value="FAILED">Failed</option>
            <option value="PENDING">Pending</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
        
        <div class="filter-group">
          <label>Job:</label>
          <select [(ngModel)]="filters.jobName" (change)="applyFilters()">
            <option value="">All Jobs</option>
            <option *ngFor="let job of uniqueJobs" [value]="job">{{ job }}</option>
          </select>
        </div>
        
        <div class="filter-group">
          <label>Date Range:</label>
          <select [(ngModel)]="filters.dateRange" (change)="applyFilters()">
            <option value="today">Today</option>
            <option value="week">Last 7 Days</option>
            <option value="month">Last 30 Days</option>
            <option value="all">All Time</option>
          </select>
        </div>
        
        <div class="filter-group search-group">
          <label>Search:</label>
          <input 
            type="text" 
            [(ngModel)]="filters.search" 
            (input)="applyFilters()"
            placeholder="Search by ID or job name...">
        </div>
      </div>
      
      <div class="stats-row">
        <div class="stat-mini">
          <span class="stat-label">Total Executions:</span>
          <span class="stat-value">{{ filteredExecutions.length }}</span>
        </div>
        <div class="stat-mini success">
          <span class="stat-label">Success:</span>
          <span class="stat-value">{{ getStatusCount('SUCCESS') }}</span>
        </div>
        <div class="stat-mini failed">
          <span class="stat-label">Failed:</span>
          <span class="stat-value">{{ getStatusCount('FAILED') }}</span>
        </div>
        <div class="stat-mini running">
          <span class="stat-label">Running:</span>
          <span class="stat-value">{{ getStatusCount('RUNNING') }}</span>
        </div>
      </div>
      
      <div class="executions-table">
        <table>
          <thead>
            <tr>
              <th (click)="sortBy('executionId')">
                Execution ID 
                <span class="sort-icon">{{ getSortIcon('executionId') }}</span>
              </th>
              <th (click)="sortBy('jobName')">
                Job Name
                <span class="sort-icon">{{ getSortIcon('jobName') }}</span>
              </th>
              <th (click)="sortBy('status')">
                Status
                <span class="sort-icon">{{ getSortIcon('status') }}</span>
              </th>
              <th (click)="sortBy('startTime')">
                Start Time
                <span class="sort-icon">{{ getSortIcon('startTime') }}</span>
              </th>
              <th (click)="sortBy('endTime')">
                End Time
                <span class="sort-icon">{{ getSortIcon('endTime') }}</span>
              </th>
              <th>Duration</th>
              <th>Progress</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let execution of paginatedExecutions" [class.row-running]="execution.status === 'RUNNING'">
              <td class="exec-id">#{{ execution.executionId }}</td>
              <td class="job-name">{{ execution.jobName }}</td>
              <td>
                <span class="status-badge" [ngClass]="'status-' + execution.status.toLowerCase()">
                  {{ execution.status }}
                </span>
              </td>
              <td class="time">{{ execution.startTime }}</td>
              <td class="time">{{ execution.endTime || '-' }}</td>
              <td class="duration">{{ execution.duration }}</td>
              <td>
                <div class="progress-bar" *ngIf="execution.status === 'RUNNING'">
                  <div class="progress-fill" [style.width.%]="execution.progress"></div>
                  <span class="progress-text">{{ execution.progress }}%</span>
                </div>
                <span *ngIf="execution.status !== 'RUNNING'">-</span>
              </td>
              <td>
                <button class="btn-action" (click)="viewLogs(execution)">Logs</button>
                <button class="btn-action" (click)="viewDetails(execution)">Details</button>
                <button 
                  *ngIf="execution.status === 'RUNNING'" 
                  class="btn-action btn-stop" 
                  (click)="stopExecution(execution)">
                  Stop
                </button>
                <button 
                  *ngIf="execution.status === 'FAILED'" 
                  class="btn-action btn-retry" 
                  (click)="retryExecution(execution)">
                  Retry
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <div class="pagination">
        <button 
          class="btn-page" 
          (click)="previousPage()" 
          [disabled]="currentPage === 1">
          Previous
        </button>
        <span class="page-info">
          Page {{ currentPage }} of {{ totalPages }}
        </span>
        <button 
          class="btn-page" 
          (click)="nextPage()" 
          [disabled]="currentPage === totalPages">
          Next
        </button>
      </div>
    </div>
  `,
  styles: [`
    .executions {
      padding: 1rem;
    }
    
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
    }
    
    .header h2 {
      color: #2c3e50;
    }
    
    .btn-refresh {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1.5rem;
      background-color: #3498db;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      transition: background-color 0.2s;
    }
    
    .btn-refresh:hover {
      background-color: #2980b9;
    }
    
    .refresh-icon {
      display: inline-block;
      transition: transform 0.3s;
    }
    
    .btn-refresh:active .refresh-icon {
      transform: rotate(360deg);
    }
    
    .filters {
      display: flex;
      gap: 1.5rem;
      padding: 1.5rem;
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
    }
    
    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 150px;
    }
    
    .filter-group label {
      font-size: 0.85rem;
      color: #666;
      font-weight: 500;
    }
    
    .filter-group select,
    .filter-group input {
      padding: 0.5rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 0.95rem;
    }
    
    .search-group {
      flex: 1;
      min-width: 250px;
    }
    
    .stats-row {
      display: flex;
      gap: 2rem;
      margin-bottom: 1.5rem;
      padding: 1rem;
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .stat-mini {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    
    .stat-label {
      color: #666;
      font-size: 0.9rem;
    }
    
    .stat-value {
      font-size: 1.2rem;
      font-weight: bold;
      color: #2c3e50;
    }
    
    .stat-mini.success .stat-value {
      color: #27ae60;
    }
    
    .stat-mini.failed .stat-value {
      color: #e74c3c;
    }
    
    .stat-mini.running .stat-value {
      color: #3498db;
    }
    
    .executions-table {
      background: white;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .executions-table table {
      width: 100%;
      border-collapse: collapse;
    }
    
    .executions-table th {
      background-color: #f8f9fa;
      padding: 1rem;
      text-align: left;
      font-weight: 600;
      color: #555;
      border-bottom: 2px solid #dee2e6;
      cursor: pointer;
      user-select: none;
    }
    
    .executions-table th:hover {
      background-color: #e9ecef;
    }
    
    .sort-icon {
      margin-left: 0.5rem;
      color: #999;
    }
    
    .executions-table td {
      padding: 1rem;
      border-bottom: 1px solid #e9ecef;
    }
    
    .executions-table tr:hover {
      background-color: #f8f9fa;
    }
    
    .row-running {
      background-color: #e3f2fd !important;
    }
    
    .exec-id {
      font-family: monospace;
      color: #666;
      font-weight: 500;
    }
    
    .job-name {
      font-weight: 500;
      color: #2c3e50;
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
    
    .status-cancelled {
      background-color: #e2e3e5;
      color: #383d41;
    }
    
    .time {
      color: #666;
      font-size: 0.9rem;
    }
    
    .duration {
      color: #666;
      font-size: 0.9rem;
      font-weight: 500;
    }
    
    .progress-bar {
      position: relative;
      width: 100px;
      height: 20px;
      background-color: #e9ecef;
      border-radius: 10px;
      overflow: hidden;
    }
    
    .progress-fill {
      position: absolute;
      top: 0;
      left: 0;
      height: 100%;
      background-color: #3498db;
      transition: width 0.3s;
    }
    
    .progress-text {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: 0.75rem;
      font-weight: 500;
      color: #333;
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
    
    .btn-retry {
      color: #28a745;
      border-color: #28a745;
    }
    
    .btn-retry:hover {
      background-color: #28a745;
      color: white;
    }
    
    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 2rem;
      margin-top: 2rem;
      padding: 1rem;
    }
    
    .btn-page {
      padding: 0.5rem 1rem;
      border: 1px solid #dee2e6;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.2s;
    }
    
    .btn-page:hover:not(:disabled) {
      background-color: #f8f9fa;
      border-color: #adb5bd;
    }
    
    .btn-page:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    
    .page-info {
      color: #666;
      font-weight: 500;
    }
  `]
})
export class ExecutionsComponent implements OnInit {
  executions: any[] = [];
  filteredExecutions: any[] = [];
  paginatedExecutions: any[] = [];
  uniqueJobs: string[] = [];
  
  filters = {
    status: '',
    jobName: '',
    dateRange: 'week',
    search: ''
  };
  
  sortField: string = 'startTime';
  sortDirection: 'asc' | 'desc' = 'desc';
  
  currentPage: number = 1;
  itemsPerPage: number = 10;
  totalPages: number = 1;
  
  ngOnInit() {
    this.loadExecutions();
    this.startAutoRefresh();
  }
  
  loadExecutions() {
    // Simulate loading execution history
    this.executions = this.generateMockExecutions();
    this.uniqueJobs = [...new Set(this.executions.map(e => e.jobName))];
    this.applyFilters();
  }
  
  generateMockExecutions(): any[] {
    const jobs = ['Daily Report', 'Data Sync', 'Backup Job', 'Email Notifications', 
                  'Cleanup Logs', 'Health Check', 'ETL Extract', 'ETL Transform', 'ETL Load'];
    const statuses = ['SUCCESS', 'RUNNING', 'FAILED', 'PENDING', 'CANCELLED'];
    const executions = [];
    
    for (let i = 0; i < 150; i++) {
      const status = statuses[Math.floor(Math.random() * statuses.length)];
      const startTime = new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000);
      const duration = Math.floor(Math.random() * 7200);
      const endTime = status === 'RUNNING' || status === 'PENDING' ? null : new Date(startTime.getTime() + duration * 1000);
      
      executions.push({
        executionId: 10500 - i,
        jobName: jobs[Math.floor(Math.random() * jobs.length)],
        status: status,
        startTime: this.formatDate(startTime),
        endTime: endTime ? this.formatDate(endTime) : null,
        duration: this.formatDuration(duration),
        progress: status === 'RUNNING' ? Math.floor(Math.random() * 100) : 0
      });
    }
    
    return executions;
  }
  
  formatDate(date: Date): string {
    return date.toISOString().replace('T', ' ').substring(0, 19);
  }
  
  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    } else {
      return `${secs}s`;
    }
  }
  
  applyFilters() {
    this.filteredExecutions = this.executions.filter(execution => {
      // Status filter
      if (this.filters.status && execution.status !== this.filters.status) {
        return false;
      }
      
      // Job name filter
      if (this.filters.jobName && execution.jobName !== this.filters.jobName) {
        return false;
      }
      
      // Search filter
      if (this.filters.search) {
        const search = this.filters.search.toLowerCase();
        if (!execution.executionId.toString().includes(search) && 
            !execution.jobName.toLowerCase().includes(search)) {
          return false;
        }
      }
      
      // Date range filter
      if (this.filters.dateRange !== 'all') {
        const executionDate = new Date(execution.startTime);
        const now = new Date();
        let daysAgo = 0;
        
        switch (this.filters.dateRange) {
          case 'today': daysAgo = 1; break;
          case 'week': daysAgo = 7; break;
          case 'month': daysAgo = 30; break;
        }
        
        const cutoffDate = new Date(now.getTime() - daysAgo * 24 * 60 * 60 * 1000);
        if (executionDate < cutoffDate) {
          return false;
        }
      }
      
      return true;
    });
    
    this.sortExecutions();
    this.updatePagination();
  }
  
  sortBy(field: string) {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }
    this.sortExecutions();
    this.updatePagination();
  }
  
  sortExecutions() {
    this.filteredExecutions.sort((a, b) => {
      let aVal = a[this.sortField];
      let bVal = b[this.sortField];
      
      if (this.sortField === 'executionId') {
        aVal = parseInt(aVal);
        bVal = parseInt(bVal);
      }
      
      if (aVal < bVal) return this.sortDirection === 'asc' ? -1 : 1;
      if (aVal > bVal) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }
  
  getSortIcon(field: string): string {
    if (this.sortField !== field) return '↕';
    return this.sortDirection === 'asc' ? '↑' : '↓';
  }
  
  updatePagination() {
    this.totalPages = Math.ceil(this.filteredExecutions.length / this.itemsPerPage);
    this.currentPage = Math.min(this.currentPage, Math.max(1, this.totalPages));
    
    const start = (this.currentPage - 1) * this.itemsPerPage;
    const end = start + this.itemsPerPage;
    this.paginatedExecutions = this.filteredExecutions.slice(start, end);
  }
  
  previousPage() {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.updatePagination();
    }
  }
  
  nextPage() {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
      this.updatePagination();
    }
  }
  
  getStatusCount(status: string): number {
    return this.filteredExecutions.filter(e => e.status === status).length;
  }
  
  refreshExecutions() {
    this.loadExecutions();
  }
  
  viewLogs(execution: any) {
    alert(`Viewing logs for execution #${execution.executionId}\n\nSample logs:\n[INFO] Job started\n[INFO] Processing data...\n[INFO] Job completed successfully`);
  }
  
  viewDetails(execution: any) {
    alert(`Execution Details:\n\nID: ${execution.executionId}\nJob: ${execution.jobName}\nStatus: ${execution.status}\nStart: ${execution.startTime}\nEnd: ${execution.endTime || 'N/A'}\nDuration: ${execution.duration}`);
  }
  
  stopExecution(execution: any) {
    execution.status = 'CANCELLED';
    alert(`Execution #${execution.executionId} has been stopped.`);
  }
  
  retryExecution(execution: any) {
    alert(`Retrying execution #${execution.executionId}...`);
    execution.status = 'PENDING';
  }
  
  startAutoRefresh() {
    setInterval(() => {
      // Update running job progress
      this.executions.forEach(exec => {
        if (exec.status === 'RUNNING') {
          exec.progress = Math.min(100, exec.progress + Math.floor(Math.random() * 10));
          if (exec.progress >= 100) {
            exec.status = 'SUCCESS';
            exec.endTime = this.formatDate(new Date());
          }
        }
      });
      this.applyFilters();
    }, 3000);
  }
}