import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="monitoring">
      <div class="header">
        <h2>System Monitoring</h2>
        <div class="header-actions">
          <span class="last-updated">Last updated: {{ lastUpdated }}</span>
          <button class="btn-refresh" (click)="refreshMetrics()">
            <span class="refresh-icon">↻</span> Refresh
          </button>
        </div>
      </div>
      
      <!-- System Health Overview -->
      <div class="health-overview">
        <div class="health-card" [ngClass]="'health-' + systemHealth.status">
          <h3>System Health</h3>
          <div class="health-status">
            <span class="health-icon">{{ getHealthIcon() }}</span>
            <span class="health-text">{{ systemHealth.status | uppercase }}</span>
          </div>
          <p class="health-message">{{ systemHealth.message }}</p>
        </div>
        
        <div class="health-card">
          <h3>Uptime</h3>
          <div class="uptime-value">{{ uptime }}</div>
          <p class="uptime-since">Since {{ startTime }}</p>
        </div>
        
        <div class="health-card">
          <h3>Active Nodes</h3>
          <div class="nodes-status">
            <span class="nodes-active">{{ activeNodes }}</span> / <span class="nodes-total">{{ totalNodes }}</span>
          </div>
          <div class="nodes-bar">
            <div class="nodes-fill" [style.width.%]="(activeNodes/totalNodes)*100"></div>
          </div>
        </div>
      </div>
      
      <!-- Performance Metrics -->
      <div class="metrics-section">
        <h3>Performance Metrics</h3>
        <div class="metrics-grid">
          <div class="metric-card">
            <h4>CPU Usage</h4>
            <div class="circular-progress" [attr.data-percentage]="metrics.cpu">
              <svg viewBox="0 0 36 36">
                <path class="circle-bg"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                />
                <path class="circle"
                  [attr.stroke-dasharray]="metrics.cpu + ', 100'"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                  [style.stroke]="getMetricColor(metrics.cpu)"
                />
              </svg>
              <div class="percentage">{{ metrics.cpu }}%</div>
            </div>
            <div class="metric-details">
              <span>Load Average: {{ metrics.loadAverage }}</span>
            </div>
          </div>
          
          <div class="metric-card">
            <h4>Memory Usage</h4>
            <div class="circular-progress" [attr.data-percentage]="metrics.memory">
              <svg viewBox="0 0 36 36">
                <path class="circle-bg"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                />
                <path class="circle"
                  [attr.stroke-dasharray]="metrics.memory + ', 100'"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                  [style.stroke]="getMetricColor(metrics.memory)"
                />
              </svg>
              <div class="percentage">{{ metrics.memory }}%</div>
            </div>
            <div class="metric-details">
              <span>{{ metrics.memoryUsed }}GB / {{ metrics.memoryTotal }}GB</span>
            </div>
          </div>
          
          <div class="metric-card">
            <h4>Disk Usage</h4>
            <div class="circular-progress" [attr.data-percentage]="metrics.disk">
              <svg viewBox="0 0 36 36">
                <path class="circle-bg"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                />
                <path class="circle"
                  [attr.stroke-dasharray]="metrics.disk + ', 100'"
                  d="M18 2.0845
                    a 15.9155 15.9155 0 0 1 0 31.831
                    a 15.9155 15.9155 0 0 1 0 -31.831"
                  stroke-width="3"
                  fill="none"
                  [style.stroke]="getMetricColor(metrics.disk)"
                />
              </svg>
              <div class="percentage">{{ metrics.disk }}%</div>
            </div>
            <div class="metric-details">
              <span>{{ metrics.diskUsed }}GB / {{ metrics.diskTotal }}GB</span>
            </div>
          </div>
          
          <div class="metric-card">
            <h4>Network I/O</h4>
            <div class="network-stats">
              <div class="network-in">
                <span class="arrow">↓</span>
                <span class="value">{{ metrics.networkIn }} MB/s</span>
              </div>
              <div class="network-out">
                <span class="arrow">↑</span>
                <span class="value">{{ metrics.networkOut }} MB/s</span>
              </div>
            </div>
            <div class="metric-details">
              <span>Total: {{ metrics.networkTotal }} GB today</span>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Job Statistics -->
      <div class="stats-section">
        <h3>Job Statistics (Last 24 Hours)</h3>
        <div class="stats-charts">
          <div class="chart-card">
            <h4>Execution Timeline</h4>
            <div class="timeline-chart">
              <div class="timeline-bar" *ngFor="let hour of hourlyStats">
                <div class="bar-container">
                  <div class="bar success" [style.height.%]="hour.success"></div>
                  <div class="bar failed" [style.height.%]="hour.failed"></div>
                  <div class="bar running" [style.height.%]="hour.running"></div>
                </div>
                <span class="hour-label">{{ hour.label }}</span>
              </div>
            </div>
            <div class="chart-legend">
              <span class="legend-item"><span class="dot success"></span> Success</span>
              <span class="legend-item"><span class="dot failed"></span> Failed</span>
              <span class="legend-item"><span class="dot running"></span> Running</span>
            </div>
          </div>
          
          <div class="chart-card">
            <h4>Job Distribution</h4>
            <div class="pie-chart">
              <svg viewBox="0 0 42 42">
                <circle cx="21" cy="21" r="15.91549430918954" fill="transparent"></circle>
                <circle cx="21" cy="21" r="15.91549430918954" fill="transparent"
                  stroke="#27ae60"
                  stroke-width="3"
                  stroke-dasharray="65 35"
                  stroke-dashoffset="25"></circle>
                <circle cx="21" cy="21" r="15.91549430918954" fill="transparent"
                  stroke="#e74c3c"
                  stroke-width="3"
                  stroke-dasharray="15 85"
                  stroke-dashoffset="90"></circle>
                <circle cx="21" cy="21" r="15.91549430918954" fill="transparent"
                  stroke="#3498db"
                  stroke-width="3"
                  stroke-dasharray="20 80"
                  stroke-dashoffset="75"></circle>
              </svg>
              <div class="pie-center">
                <span class="total-jobs">{{ totalJobsToday }}</span>
                <span class="label">Total</span>
              </div>
            </div>
            <div class="pie-stats">
              <div class="stat-item">
                <span class="dot success"></span>
                <span>Success: {{ jobStats.success }}%</span>
              </div>
              <div class="stat-item">
                <span class="dot failed"></span>
                <span>Failed: {{ jobStats.failed }}%</span>
              </div>
              <div class="stat-item">
                <span class="dot running"></span>
                <span>Other: {{ jobStats.other }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Active Alerts -->
      <div class="alerts-section">
        <h3>Active Alerts</h3>
        <div class="alerts-list">
          <div *ngIf="alerts.length === 0" class="no-alerts">
            <span class="check-icon">✓</span> No active alerts
          </div>
          <div *ngFor="let alert of alerts" class="alert-item" [ngClass]="'alert-' + alert.severity">
            <span class="alert-icon">{{ getAlertIcon(alert.severity) }}</span>
            <div class="alert-content">
              <div class="alert-title">{{ alert.title }}</div>
              <div class="alert-message">{{ alert.message }}</div>
              <div class="alert-time">{{ alert.time }}</div>
            </div>
            <button class="btn-dismiss" (click)="dismissAlert(alert)">×</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .monitoring {
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
    
    .header-actions {
      display: flex;
      align-items: center;
      gap: 1.5rem;
    }
    
    .last-updated {
      color: #666;
      font-size: 0.9rem;
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
    
    /* Health Overview */
    .health-overview {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2rem;
    }
    
    .health-card {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .health-card h3 {
      margin-bottom: 1rem;
      color: #555;
      font-size: 1rem;
    }
    
    .health-status {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-bottom: 0.5rem;
    }
    
    .health-icon {
      font-size: 2rem;
    }
    
    .health-text {
      font-size: 1.5rem;
      font-weight: bold;
    }
    
    .health-good {
      background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%);
    }
    
    .health-good .health-text {
      color: #155724;
    }
    
    .health-warning {
      background: linear-gradient(135deg, #fff3cd 0%, #ffeaa7 100%);
    }
    
    .health-warning .health-text {
      color: #856404;
    }
    
    .health-critical {
      background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%);
    }
    
    .health-critical .health-text {
      color: #721c24;
    }
    
    .health-message {
      color: #666;
      font-size: 0.9rem;
    }
    
    .uptime-value {
      font-size: 1.8rem;
      font-weight: bold;
      color: #2c3e50;
      margin-bottom: 0.5rem;
    }
    
    .uptime-since {
      color: #666;
      font-size: 0.9rem;
    }
    
    .nodes-status {
      font-size: 1.5rem;
      margin-bottom: 0.5rem;
    }
    
    .nodes-active {
      color: #27ae60;
      font-weight: bold;
    }
    
    .nodes-total {
      color: #666;
    }
    
    .nodes-bar {
      height: 8px;
      background: #e9ecef;
      border-radius: 4px;
      overflow: hidden;
    }
    
    .nodes-fill {
      height: 100%;
      background: #27ae60;
      transition: width 0.3s;
    }
    
    /* Metrics Section */
    .metrics-section {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      margin-bottom: 2rem;
    }
    
    .metrics-section h3 {
      margin-bottom: 1.5rem;
      color: #2c3e50;
    }
    
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 2rem;
    }
    
    .metric-card {
      text-align: center;
    }
    
    .metric-card h4 {
      margin-bottom: 1rem;
      color: #555;
      font-size: 0.95rem;
    }
    
    .circular-progress {
      position: relative;
      width: 120px;
      height: 120px;
      margin: 0 auto 1rem;
    }
    
    .circular-progress svg {
      transform: rotate(-90deg);
    }
    
    .circle-bg {
      stroke: #e9ecef;
    }
    
    .circle {
      stroke-linecap: round;
      transition: stroke-dasharray 0.3s;
    }
    
    .percentage {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: 1.5rem;
      font-weight: bold;
      color: #2c3e50;
    }
    
    .metric-details {
      color: #666;
      font-size: 0.85rem;
    }
    
    .network-stats {
      display: flex;
      justify-content: center;
      gap: 2rem;
      margin-bottom: 1rem;
    }
    
    .network-in, .network-out {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    
    .network-in .arrow {
      color: #3498db;
      font-size: 1.5rem;
    }
    
    .network-out .arrow {
      color: #e74c3c;
      font-size: 1.5rem;
    }
    
    .network-stats .value {
      font-size: 1.2rem;
      font-weight: bold;
    }
    
    /* Stats Section */
    .stats-section {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      margin-bottom: 2rem;
    }
    
    .stats-section h3 {
      margin-bottom: 1.5rem;
      color: #2c3e50;
    }
    
    .stats-charts {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 2rem;
    }
    
    @media (max-width: 768px) {
      .stats-charts {
        grid-template-columns: 1fr;
      }
    }
    
    .chart-card {
      padding: 1rem;
      border: 1px solid #e9ecef;
      border-radius: 8px;
    }
    
    .chart-card h4 {
      margin-bottom: 1rem;
      color: #555;
      font-size: 0.95rem;
    }
    
    .timeline-chart {
      display: flex;
      align-items: flex-end;
      height: 150px;
      gap: 2px;
      margin-bottom: 0.5rem;
    }
    
    .timeline-bar {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    
    .bar-container {
      width: 100%;
      height: 120px;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
    }
    
    .bar {
      width: 100%;
      transition: height 0.3s;
    }
    
    .bar.success {
      background: #27ae60;
    }
    
    .bar.failed {
      background: #e74c3c;
    }
    
    .bar.running {
      background: #3498db;
    }
    
    .hour-label {
      font-size: 0.7rem;
      color: #999;
      margin-top: 0.25rem;
    }
    
    .chart-legend {
      display: flex;
      justify-content: center;
      gap: 1.5rem;
      margin-top: 1rem;
    }
    
    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
      color: #666;
    }
    
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }
    
    .dot.success {
      background: #27ae60;
    }
    
    .dot.failed {
      background: #e74c3c;
    }
    
    .dot.running {
      background: #3498db;
    }
    
    .pie-chart {
      position: relative;
      width: 150px;
      height: 150px;
      margin: 0 auto 1rem;
    }
    
    .pie-chart svg {
      transform: rotate(-90deg);
    }
    
    .pie-center {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
    }
    
    .total-jobs {
      display: block;
      font-size: 1.8rem;
      font-weight: bold;
      color: #2c3e50;
    }
    
    .pie-center .label {
      font-size: 0.85rem;
      color: #666;
    }
    
    .pie-stats {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    
    .stat-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
      color: #666;
    }
    
    /* Alerts Section */
    .alerts-section {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .alerts-section h3 {
      margin-bottom: 1.5rem;
      color: #2c3e50;
    }
    
    .no-alerts {
      padding: 2rem;
      text-align: center;
      color: #27ae60;
      font-size: 1.1rem;
    }
    
    .check-icon {
      display: inline-block;
      margin-right: 0.5rem;
      font-size: 1.5rem;
    }
    
    .alert-item {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1rem;
      margin-bottom: 1rem;
      border-radius: 8px;
      border-left: 4px solid;
    }
    
    .alert-warning {
      background: #fff3cd;
      border-color: #ffc107;
    }
    
    .alert-error {
      background: #f8d7da;
      border-color: #dc3545;
    }
    
    .alert-info {
      background: #d1ecf1;
      border-color: #17a2b8;
    }
    
    .alert-icon {
      font-size: 1.5rem;
    }
    
    .alert-content {
      flex: 1;
    }
    
    .alert-title {
      font-weight: 600;
      margin-bottom: 0.25rem;
      color: #333;
    }
    
    .alert-message {
      color: #666;
      font-size: 0.9rem;
      margin-bottom: 0.25rem;
    }
    
    .alert-time {
      color: #999;
      font-size: 0.85rem;
    }
    
    .btn-dismiss {
      background: none;
      border: none;
      font-size: 1.5rem;
      color: #999;
      cursor: pointer;
      padding: 0;
      width: 30px;
      height: 30px;
    }
    
    .btn-dismiss:hover {
      color: #333;
    }
  `]
})
export class MonitoringComponent implements OnInit, OnDestroy {
  lastUpdated: string = '';
  systemHealth = {
    status: 'good',
    message: 'All systems operational'
  };
  
  uptime: string = '14d 7h 32m';
  startTime: string = '2024-12-25 10:28:15';
  activeNodes: number = 5;
  totalNodes: number = 6;
  
  metrics = {
    cpu: 45,
    memory: 62,
    disk: 38,
    networkIn: 12.5,
    networkOut: 8.3,
    networkTotal: 245,
    loadAverage: '2.45',
    memoryUsed: 10,
    memoryTotal: 16,
    diskUsed: 152,
    diskTotal: 400
  };
  
  hourlyStats: any[] = [];
  totalJobsToday: number = 324;
  jobStats = {
    success: 65,
    failed: 15,
    other: 20
  };
  
  alerts: any[] = [];
  
  private intervalId: any;
  
  ngOnInit() {
    this.generateHourlyStats();
    this.loadAlerts();
    this.updateLastUpdated();
    this.startAutoRefresh();
  }
  
  ngOnDestroy() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }
  
  generateHourlyStats() {
    const hours = ['12am', '2am', '4am', '6am', '8am', '10am', '12pm', '2pm', '4pm', '6pm', '8pm', '10pm'];
    this.hourlyStats = hours.map(label => ({
      label,
      success: Math.floor(Math.random() * 60) + 20,
      failed: Math.floor(Math.random() * 20),
      running: Math.floor(Math.random() * 15)
    }));
  }
  
  loadAlerts() {
    this.alerts = [
      {
        id: 1,
        severity: 'warning',
        title: 'High Memory Usage',
        message: 'Memory usage on Node 3 has exceeded 85% threshold',
        time: '10 minutes ago'
      },
      {
        id: 2,
        severity: 'error',
        title: 'Job Failure Rate High',
        message: 'ETL Transform job has failed 3 times in the last hour',
        time: '25 minutes ago'
      },
      {
        id: 3,
        severity: 'info',
        title: 'Scheduled Maintenance',
        message: 'System maintenance scheduled for tomorrow at 2:00 AM',
        time: '1 hour ago'
      }
    ];
  }
  
  getHealthIcon(): string {
    switch (this.systemHealth.status) {
      case 'good': return '✓';
      case 'warning': return '⚠';
      case 'critical': return '✗';
      default: return '•';
    }
  }
  
  getAlertIcon(severity: string): string {
    switch (severity) {
      case 'warning': return '⚠';
      case 'error': return '✗';
      case 'info': return 'ℹ';
      default: return '•';
    }
  }
  
  getMetricColor(value: number): string {
    if (value < 50) return '#27ae60';
    if (value < 80) return '#f39c12';
    return '#e74c3c';
  }
  
  updateLastUpdated() {
    const now = new Date();
    this.lastUpdated = now.toLocaleTimeString();
  }
  
  refreshMetrics() {
    // Simulate refreshing metrics
    this.metrics.cpu = Math.floor(Math.random() * 30) + 35;
    this.metrics.memory = Math.floor(Math.random() * 30) + 50;
    this.metrics.disk = Math.floor(Math.random() * 20) + 30;
    this.metrics.networkIn = Math.round(Math.random() * 20 * 10) / 10;
    this.metrics.networkOut = Math.round(Math.random() * 15 * 10) / 10;
    
    this.updateLastUpdated();
    this.checkSystemHealth();
  }
  
  checkSystemHealth() {
    if (this.metrics.cpu > 80 || this.metrics.memory > 85) {
      this.systemHealth = {
        status: 'critical',
        message: 'High resource usage detected'
      };
    } else if (this.metrics.cpu > 60 || this.metrics.memory > 70) {
      this.systemHealth = {
        status: 'warning',
        message: 'Moderate resource usage'
      };
    } else {
      this.systemHealth = {
        status: 'good',
        message: 'All systems operational'
      };
    }
  }
  
  dismissAlert(alert: any) {
    this.alerts = this.alerts.filter(a => a.id !== alert.id);
  }
  
  startAutoRefresh() {
    this.intervalId = setInterval(() => {
      this.refreshMetrics();
      
      // Update uptime
      const parts = this.uptime.match(/(\d+)d (\d+)h (\d+)m/);
      if (parts) {
        let minutes = parseInt(parts[3]) + 1;
        let hours = parseInt(parts[2]);
        let days = parseInt(parts[1]);
        
        if (minutes >= 60) {
          minutes = 0;
          hours++;
        }
        if (hours >= 24) {
          hours = 0;
          days++;
        }
        
        this.uptime = `${days}d ${hours}h ${minutes}m`;
      }
    }, 10000); // Refresh every 10 seconds
  }
}