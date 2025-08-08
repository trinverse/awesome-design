import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JobService } from '../../services/job.service';

interface JobNode {
  id: number;
  name: string;
  type: string;
  status: string;
  dependencies: number[];
  dependents: number[];
  x?: number;
  y?: number;
  level?: number;
}

@Component({
  selector: 'app-job-dependencies',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="dependencies-container">
      <div class="header">
        <h2>Job Dependencies</h2>
        <div class="view-controls">
          <button [class.active]="viewMode === 'graph'" (click)="setViewMode('graph')">
            Graph View
          </button>
          <button [class.active]="viewMode === 'list'" (click)="setViewMode('list')">
            List View
          </button>
          <button [class.active]="viewMode === 'matrix'" (click)="setViewMode('matrix')">
            Matrix View
          </button>
        </div>
      </div>

      <!-- Graph View -->
      <div *ngIf="viewMode === 'graph'" class="graph-view">
        <div class="graph-controls">
          <button (click)="autoLayout()">Auto Layout</button>
          <button (click)="zoomIn()">Zoom In</button>
          <button (click)="zoomOut()">Zoom Out</button>
          <button (click)="resetZoom()">Reset</button>
        </div>
        
        <div class="graph-container" [style.transform]="'scale(' + zoomLevel + ')'">
          <svg class="dependency-graph" [attr.viewBox]="'0 0 ' + svgWidth + ' ' + svgHeight">
            <!-- Draw connections -->
            <g class="connections">
              <line *ngFor="let connection of connections"
                [attr.x1]="connection.x1"
                [attr.y1]="connection.y1"
                [attr.x2]="connection.x2"
                [attr.y2]="connection.y2"
                class="dependency-line"
                [class.active]="connection.active"
                [class.blocked]="connection.blocked"
                marker-end="url(#arrowhead)"/>
            </g>
            
            <!-- Arrow marker definition -->
            <defs>
              <marker id="arrowhead" markerWidth="10" markerHeight="7" 
                refX="9" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#666" />
              </marker>
            </defs>
            
            <!-- Draw nodes -->
            <g class="nodes">
              <g *ngFor="let job of jobNodes" 
                class="job-node"
                [attr.transform]="'translate(' + job.x + ',' + job.y + ')'"
                (click)="selectJob(job)">
                
                <rect x="-60" y="-25" width="120" height="50" 
                  class="node-box"
                  [class.selected]="selectedJob?.id === job.id"
                  [class.status-success]="job.status === 'SUCCESS'"
                  [class.status-running]="job.status === 'RUNNING'"
                  [class.status-failed]="job.status === 'FAILED'"
                  [class.status-waiting]="job.status === 'WAITING'"/>
                
                <text y="-5" text-anchor="middle" class="job-name">
                  {{ job.name }}
                </text>
                <text y="10" text-anchor="middle" class="job-type">
                  {{ job.type }}
                </text>
              </g>
            </g>
          </svg>
        </div>
        
        <!-- Job Details Panel -->
        <div *ngIf="selectedJob" class="job-details-panel">
          <h3>{{ selectedJob.name }}</h3>
          <div class="detail-item">
            <label>Type:</label>
            <span>{{ selectedJob.type }}</span>
          </div>
          <div class="detail-item">
            <label>Status:</label>
            <span class="status-badge" [class]="'status-' + selectedJob.status.toLowerCase()">
              {{ selectedJob.status }}
            </span>
          </div>
          <div class="detail-item">
            <label>Dependencies:</label>
            <div class="dependency-list">
              <span *ngIf="selectedJob.dependencies.length === 0" class="no-deps">None</span>
              <div *ngFor="let depId of selectedJob.dependencies" class="dep-item">
                → {{ getJobName(depId) }}
              </div>
            </div>
          </div>
          <div class="detail-item">
            <label>Dependent Jobs:</label>
            <div class="dependency-list">
              <span *ngIf="selectedJob.dependents.length === 0" class="no-deps">None</span>
              <div *ngFor="let depId of selectedJob.dependents" class="dep-item">
                ← {{ getJobName(depId) }}
              </div>
            </div>
          </div>
          <div class="actions">
            <button class="btn-edit" (click)="editDependencies(selectedJob)">
              Edit Dependencies
            </button>
            <button class="btn-validate" (click)="validateDependencies(selectedJob)">
              Validate Chain
            </button>
          </div>
        </div>
      </div>

      <!-- List View -->
      <div *ngIf="viewMode === 'list'" class="list-view">
        <div class="dependency-table">
          <table>
            <thead>
              <tr>
                <th>Job Name</th>
                <th>Type</th>
                <th>Dependencies</th>
                <th>Dependent Jobs</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let job of jobs">
                <td class="job-name">{{ job.name }}</td>
                <td>{{ job.type }}</td>
                <td>
                  <div class="dep-badges">
                    <span *ngIf="job.dependencies.length === 0" class="no-deps">-</span>
                    <span *ngFor="let dep of job.dependencies" class="dep-badge">
                      {{ dep.name }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="dep-badges">
                    <span *ngIf="!getJobDependents(job.id).length" class="no-deps">-</span>
                    <span *ngFor="let dep of getJobDependents(job.id)" class="dep-badge dependent">
                      {{ dep.name }}
                    </span>
                  </div>
                </td>
                <td>
                  <span class="status-badge" [class]="'status-' + job.status.toLowerCase()">
                    {{ job.status }}
                  </span>
                </td>
                <td>
                  <button class="btn-action" (click)="editDependencies(job)">Edit</button>
                  <button class="btn-action" (click)="validateDependencies(job)">Validate</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Matrix View -->
      <div *ngIf="viewMode === 'matrix'" class="matrix-view">
        <div class="matrix-container">
          <table class="dependency-matrix">
            <thead>
              <tr>
                <th class="corner-cell">Job / Depends On →</th>
                <th *ngFor="let job of jobs" class="rotate">
                  <div>{{ job.name }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let job of jobs">
                <td class="job-name-cell">{{ job.name }}</td>
                <td *ngFor="let depJob of jobs" class="matrix-cell"
                  [class.has-dependency]="hasDependency(job.id, depJob.id)"
                  [class.is-self]="job.id === depJob.id"
                  (click)="toggleDependency(job, depJob)">
                  <span *ngIf="hasDependency(job.id, depJob.id)">✓</span>
                  <span *ngIf="job.id === depJob.id">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <div class="matrix-legend">
          <div class="legend-item">
            <span class="legend-box has-dependency"></span> Has Dependency
          </div>
          <div class="legend-item">
            <span class="legend-box is-self"></span> Same Job (No self-dependency)
          </div>
          <div class="legend-item">
            <span class="legend-box"></span> No Dependency
          </div>
        </div>
      </div>

      <!-- Validation Results -->
      <div *ngIf="validationResults" class="validation-results" [class.has-errors]="validationResults.hasErrors">
        <h3>Validation Results</h3>
        <div class="validation-content">
          <div *ngIf="!validationResults.hasErrors" class="success-message">
            ✓ All dependencies are valid
          </div>
          <div *ngIf="validationResults.hasErrors" class="error-list">
            <div *ngFor="let error of validationResults.errors" class="error-item">
              <span class="error-icon">⚠</span>
              {{ error }}
            </div>
          </div>
          <div *ngIf="validationResults.warnings.length > 0" class="warning-list">
            <h4>Warnings:</h4>
            <div *ngFor="let warning of validationResults.warnings" class="warning-item">
              <span class="warning-icon">ℹ</span>
              {{ warning }}
            </div>
          </div>
        </div>
        <button class="btn-close" (click)="validationResults = null">Close</button>
      </div>
    </div>
  `,
  styles: [`
    .dependencies-container {
      padding: 1rem;
      height: calc(100vh - 100px);
      display: flex;
      flex-direction: column;
    }
    
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    
    .header h2 {
      color: #2c3e50;
    }
    
    .view-controls {
      display: flex;
      gap: 0.5rem;
    }
    
    .view-controls button {
      padding: 0.5rem 1rem;
      border: 1px solid #ddd;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.2s;
    }
    
    .view-controls button:hover {
      background: #f8f9fa;
    }
    
    .view-controls button.active {
      background: #3498db;
      color: white;
      border-color: #3498db;
    }
    
    /* Graph View */
    .graph-view {
      flex: 1;
      display: flex;
      gap: 1.5rem;
      background: white;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .graph-controls {
      position: absolute;
      top: 1rem;
      right: 1rem;
      display: flex;
      gap: 0.5rem;
      z-index: 10;
    }
    
    .graph-controls button {
      padding: 0.5rem 1rem;
      border: 1px solid #ddd;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.85rem;
    }
    
    .graph-controls button:hover {
      background: #f8f9fa;
    }
    
    .graph-container {
      flex: 1;
      position: relative;
      overflow: auto;
      border: 1px solid #e9ecef;
      border-radius: 8px;
      background: #fafbfc;
      transition: transform 0.3s;
    }
    
    .dependency-graph {
      width: 100%;
      height: 100%;
      min-height: 500px;
    }
    
    .dependency-line {
      stroke: #999;
      stroke-width: 2;
      fill: none;
    }
    
    .dependency-line.active {
      stroke: #3498db;
      stroke-width: 3;
    }
    
    .dependency-line.blocked {
      stroke: #e74c3c;
      stroke-dasharray: 5, 5;
    }
    
    .job-node {
      cursor: pointer;
    }
    
    .node-box {
      fill: white;
      stroke: #ddd;
      stroke-width: 2;
      rx: 4;
      transition: all 0.2s;
    }
    
    .node-box:hover {
      stroke: #3498db;
      stroke-width: 3;
    }
    
    .node-box.selected {
      stroke: #2980b9;
      stroke-width: 3;
      fill: #ebf5fb;
    }
    
    .node-box.status-success {
      fill: #d4edda;
      stroke: #27ae60;
    }
    
    .node-box.status-running {
      fill: #cce5ff;
      stroke: #3498db;
    }
    
    .node-box.status-failed {
      fill: #f8d7da;
      stroke: #e74c3c;
    }
    
    .node-box.status-waiting {
      fill: #fff3cd;
      stroke: #f39c12;
    }
    
    .job-name {
      font-size: 12px;
      font-weight: 600;
      fill: #2c3e50;
    }
    
    .job-type {
      font-size: 10px;
      fill: #666;
    }
    
    /* Job Details Panel */
    .job-details-panel {
      width: 300px;
      background: white;
      border: 1px solid #e9ecef;
      border-radius: 8px;
      padding: 1.5rem;
    }
    
    .job-details-panel h3 {
      margin-bottom: 1rem;
      color: #2c3e50;
    }
    
    .detail-item {
      margin-bottom: 1rem;
    }
    
    .detail-item label {
      display: block;
      font-size: 0.85rem;
      color: #666;
      margin-bottom: 0.25rem;
    }
    
    .dependency-list {
      margin-top: 0.5rem;
    }
    
    .dep-item {
      padding: 0.25rem 0;
      color: #2c3e50;
    }
    
    .no-deps {
      color: #999;
      font-style: italic;
    }
    
    .actions {
      margin-top: 1.5rem;
      display: flex;
      gap: 0.5rem;
    }
    
    .btn-edit, .btn-validate {
      flex: 1;
      padding: 0.5rem;
      border: 1px solid #3498db;
      background: white;
      color: #3498db;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.85rem;
    }
    
    .btn-edit:hover, .btn-validate:hover {
      background: #3498db;
      color: white;
    }
    
    /* List View */
    .list-view {
      background: white;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .dependency-table {
      overflow-x: auto;
    }
    
    .dependency-table table {
      width: 100%;
      border-collapse: collapse;
    }
    
    .dependency-table th {
      background: #f8f9fa;
      padding: 1rem;
      text-align: left;
      font-weight: 600;
      color: #555;
      border-bottom: 2px solid #dee2e6;
    }
    
    .dependency-table td {
      padding: 1rem;
      border-bottom: 1px solid #e9ecef;
    }
    
    .dep-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
    }
    
    .dep-badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      background: #e9ecef;
      border-radius: 12px;
      font-size: 0.85rem;
      color: #495057;
    }
    
    .dep-badge.dependent {
      background: #d1ecf1;
      color: #0c5460;
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
    
    .status-waiting {
      background-color: #fff3cd;
      color: #856404;
    }
    
    .btn-action {
      padding: 0.25rem 0.75rem;
      margin-right: 0.5rem;
      border: 1px solid #dee2e6;
      background: white;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.85rem;
    }
    
    .btn-action:hover {
      background: #f8f9fa;
      border-color: #adb5bd;
    }
    
    /* Matrix View */
    .matrix-view {
      background: white;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .matrix-container {
      overflow: auto;
      max-height: 500px;
      margin-bottom: 1rem;
    }
    
    .dependency-matrix {
      border-collapse: collapse;
    }
    
    .dependency-matrix th,
    .dependency-matrix td {
      border: 1px solid #dee2e6;
      padding: 0.5rem;
      text-align: center;
    }
    
    .corner-cell {
      background: #f8f9fa;
      font-weight: 600;
    }
    
    .rotate {
      height: 120px;
      white-space: nowrap;
      background: #f8f9fa;
    }
    
    .rotate > div {
      transform: rotate(-45deg);
      width: 30px;
    }
    
    .job-name-cell {
      background: #f8f9fa;
      font-weight: 600;
      text-align: left;
    }
    
    .matrix-cell {
      cursor: pointer;
      width: 40px;
      height: 40px;
    }
    
    .matrix-cell:hover:not(.is-self) {
      background: #e9ecef;
    }
    
    .matrix-cell.has-dependency {
      background: #d4edda;
      color: #155724;
      font-weight: bold;
    }
    
    .matrix-cell.is-self {
      background: #e2e3e5;
      cursor: not-allowed;
    }
    
    .matrix-legend {
      display: flex;
      gap: 2rem;
      justify-content: center;
      padding: 1rem;
      background: #f8f9fa;
      border-radius: 4px;
    }
    
    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    
    .legend-box {
      width: 20px;
      height: 20px;
      border: 1px solid #dee2e6;
      background: white;
    }
    
    .legend-box.has-dependency {
      background: #d4edda;
    }
    
    .legend-box.is-self {
      background: #e2e3e5;
    }
    
    /* Validation Results */
    .validation-results {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: white;
      border-radius: 8px;
      padding: 2rem;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      z-index: 1000;
      min-width: 400px;
      max-width: 600px;
    }
    
    .validation-results h3 {
      margin-bottom: 1rem;
      color: #2c3e50;
    }
    
    .success-message {
      color: #27ae60;
      font-size: 1.1rem;
      padding: 1rem;
      background: #d4edda;
      border-radius: 4px;
    }
    
    .error-list, .warning-list {
      margin: 1rem 0;
    }
    
    .error-item, .warning-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem;
      margin-bottom: 0.5rem;
      border-radius: 4px;
    }
    
    .error-item {
      background: #f8d7da;
      color: #721c24;
    }
    
    .warning-item {
      background: #fff3cd;
      color: #856404;
    }
    
    .error-icon, .warning-icon {
      font-size: 1.2rem;
    }
    
    .btn-close {
      margin-top: 1rem;
      padding: 0.5rem 1.5rem;
      background: #3498db;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }
    
    .btn-close:hover {
      background: #2980b9;
    }
  `]
})
export class JobDependenciesComponent implements OnInit {
  viewMode: 'graph' | 'list' | 'matrix' = 'graph';
  jobs: any[] = [];
  jobNodes: JobNode[] = [];
  connections: any[] = [];
  selectedJob: JobNode | null = null;
  validationResults: any = null;
  zoomLevel: number = 1;
  svgWidth: number = 800;
  svgHeight: number = 600;

  constructor(private jobService: JobService) {}

  ngOnInit() {
    this.loadJobs();
  }

  loadJobs() {
    this.jobService.getJobsWithDependencies().subscribe(jobs => {
      this.jobs = jobs;
      this.buildJobNodes();
      this.calculateLayout();
      this.buildConnections();
    });
  }

  buildJobNodes() {
    this.jobNodes = this.jobs.map(job => ({
      id: job.id,
      name: job.name,
      type: job.type,
      status: this.getJobStatus(job),
      dependencies: job.dependencies ? job.dependencies.map((d: any) => d.id) : [],
      dependents: this.getJobDependents(job.id).map(d => d.id)
    }));
  }

  getJobStatus(job: any): string {
    // Simulate job status based on dependencies
    if (job.dependencies && job.dependencies.length > 0) {
      const allDepsComplete = job.dependencies.every((dep: any) => 
        this.getJobStatus(this.jobs.find(j => j.id === dep.id)) === 'SUCCESS'
      );
      if (!allDepsComplete) return 'WAITING';
    }
    
    // Random status for demo
    const statuses = ['SUCCESS', 'RUNNING', 'FAILED', 'WAITING'];
    return statuses[Math.floor(Math.random() * statuses.length)];
  }

  calculateLayout() {
    // Topological sort for layered layout
    const levels = this.topologicalSort();
    const levelWidth = 150;
    const nodeHeight = 80;
    
    levels.forEach((level, levelIndex) => {
      const levelX = 100 + levelIndex * levelWidth;
      const levelStartY = (this.svgHeight - level.length * nodeHeight) / 2;
      
      level.forEach((nodeId, nodeIndex) => {
        const node = this.jobNodes.find(n => n.id === nodeId);
        if (node) {
          node.x = levelX;
          node.y = levelStartY + nodeIndex * nodeHeight + nodeHeight / 2;
          node.level = levelIndex;
        }
      });
    });
    
    // Adjust SVG size based on layout
    this.svgWidth = Math.max(800, (levels.length + 1) * levelWidth);
    this.svgHeight = Math.max(600, Math.max(...levels.map(l => l.length)) * nodeHeight + 100);
  }

  topologicalSort(): number[][] {
    const visited = new Set<number>();
    const levels: number[][] = [];
    const nodeLevel = new Map<number, number>();
    
    // Find nodes with no dependencies (starting nodes)
    const startNodes = this.jobNodes.filter(node => node.dependencies.length === 0);
    
    if (startNodes.length === 0) {
      // If no start nodes, might have circular dependency
      // For demo, just use all nodes
      return [this.jobNodes.map(n => n.id)];
    }
    
    // BFS to assign levels
    const queue: {id: number, level: number}[] = startNodes.map(n => ({id: n.id, level: 0}));
    
    while (queue.length > 0) {
      const {id, level} = queue.shift()!;
      
      if (visited.has(id)) continue;
      visited.add(id);
      
      nodeLevel.set(id, level);
      
      if (!levels[level]) levels[level] = [];
      levels[level].push(id);
      
      // Add dependent nodes to queue
      const dependents = this.getJobDependents(id);
      dependents.forEach(dep => {
        if (!visited.has(dep.id)) {
          queue.push({id: dep.id, level: level + 1});
        }
      });
    }
    
    // Add any unvisited nodes to the last level
    const unvisited = this.jobNodes.filter(n => !visited.has(n.id));
    if (unvisited.length > 0) {
      levels.push(unvisited.map(n => n.id));
    }
    
    return levels;
  }

  buildConnections() {
    this.connections = [];
    
    this.jobNodes.forEach(node => {
      node.dependencies.forEach(depId => {
        const depNode = this.jobNodes.find(n => n.id === depId);
        if (depNode && node.x && node.y && depNode.x && depNode.y) {
          this.connections.push({
            x1: depNode.x + 60, // Right edge of dependency
            y1: depNode.y,
            x2: node.x - 60, // Left edge of dependent
            y2: node.y,
            active: this.selectedJob?.id === node.id || this.selectedJob?.id === depId,
            blocked: depNode.status === 'FAILED'
          });
        }
      });
    });
  }

  setViewMode(mode: 'graph' | 'list' | 'matrix') {
    this.viewMode = mode;
  }

  selectJob(job: JobNode) {
    this.selectedJob = this.selectedJob?.id === job.id ? null : job;
    this.buildConnections(); // Rebuild to highlight connections
  }

  getJobName(id: number): string {
    const job = this.jobNodes.find(j => j.id === id);
    return job ? job.name : 'Unknown';
  }

  getJobDependents(jobId: number): any[] {
    return this.jobs.filter(job => 
      job.dependencies && job.dependencies.some((dep: any) => dep.id === jobId)
    );
  }

  hasDependency(jobId: number, depId: number): boolean {
    const job = this.jobs.find(j => j.id === jobId);
    return job && job.dependencies ? 
      job.dependencies.some((dep: any) => dep.id === depId) : false;
  }

  toggleDependency(job: any, depJob: any) {
    if (job.id === depJob.id) return; // Can't depend on self
    
    // Check if would create circular dependency
    if (this.wouldCreateCircularDependency(job.id, depJob.id)) {
      this.validationResults = {
        hasErrors: true,
        errors: [`Adding this dependency would create a circular dependency between ${job.name} and ${depJob.name}`],
        warnings: []
      };
      return;
    }
    
    const hasDepfunction: any= this.hasDependency(job.id, depJob.id);
    
    if (hasDepfunction) {
      // Remove dependency
      job.dependencies = job.dependencies.filter((dep: any) => dep.id !== depJob.id);
    } else {
      // Add dependency
      if (!job.dependencies) job.dependencies = [];
      job.dependencies.push(depJob);
    }
    
    // Update and rebuild
    this.buildJobNodes();
    this.calculateLayout();
    this.buildConnections();
    
    // Save changes
    this.jobService.updateJobDependencies(job.id, job.dependencies).subscribe();
  }

  wouldCreateCircularDependency(jobId: number, newDepId: number): boolean {
    // Check if newDepId already depends on jobId (directly or indirectly)
    const visited = new Set<number>();
    const queue = [newDepId];
    
    while (queue.length > 0) {
      const currentId = queue.shift()!;
      
      if (currentId === jobId) return true; // Circular dependency found
      
      if (visited.has(currentId)) continue;
      visited.add(currentId);
      
      const current = this.jobs.find(j => j.id === currentId);
      if (current && current.dependencies) {
        queue.push(...current.dependencies.map((dep: any) => dep.id));
      }
    }
    
    return false;
  }

  editDependencies(job: any) {
    // Open modal to edit dependencies
    console.log('Edit dependencies for', job);
    // This would open a modal similar to job edit modal
  }

  validateDependencies(job: any) {
    const errors: string[] = [];
    const warnings: string[] = [];
    
    // Check for circular dependencies
    if (this.hasCircularDependency(job.id)) {
      errors.push(`Job "${job.name}" is part of a circular dependency chain`);
    }
    
    // Check for missing dependencies
    if (job.dependencies) {
      job.dependencies.forEach((dep: any) => {
        if (!this.jobs.find(j => j.id === dep.id)) {
          errors.push(`Dependency "${dep.name}" not found`);
        }
      });
    }
    
    // Check dependency depth
    const depth = this.getMaxDependencyDepth(job.id);
    if (depth > 5) {
      warnings.push(`Deep dependency chain detected (${depth} levels). This may cause delays.`);
    }
    
    // Check for redundant dependencies
    const redundant = this.findRedundantDependencies(job);
    if (redundant.length > 0) {
      warnings.push(`Redundant dependencies detected: ${redundant.join(', ')}`);
    }
    
    this.validationResults = {
      hasErrors: errors.length > 0,
      errors,
      warnings
    };
  }

  hasCircularDependency(jobId: number, visited: Set<number> = new Set(), path: Set<number> = new Set()): boolean {
    if (path.has(jobId)) return true;
    if (visited.has(jobId)) return false;
    
    visited.add(jobId);
    path.add(jobId);
    
    const job = this.jobs.find(j => j.id === jobId);
    if (job && job.dependencies) {
      for (const dep of job.dependencies) {
        if (this.hasCircularDependency(dep.id, visited, path)) {
          return true;
        }
      }
    }
    
    path.delete(jobId);
    return false;
  }

  getMaxDependencyDepth(jobId: number, visited: Set<number> = new Set()): number {
    if (visited.has(jobId)) return 0;
    visited.add(jobId);
    
    const job = this.jobs.find(j => j.id === jobId);
    if (!job || !job.dependencies || job.dependencies.length === 0) {
      return 0;
    }
    
    const depths = job.dependencies.map((dep: any) => 
      this.getMaxDependencyDepth(dep.id, visited)
    );
    
    return 1 + Math.max(...depths);
  }

  findRedundantDependencies(job: any): string[] {
    const redundant: string[] = [];
    
    if (!job.dependencies || job.dependencies.length < 2) return redundant;
    
    // Check if any dependency is also a dependency of another dependency
    job.dependencies.forEach((dep1: any) => {
      job.dependencies.forEach((dep2: any) => {
        if (dep1.id !== dep2.id) {
          if (this.isDependencyOf(dep1.id, dep2.id)) {
            redundant.push(dep1.name);
          }
        }
      });
    });
    
    return [...new Set(redundant)];
  }

  isDependencyOf(jobId: number, potentialDepId: number): boolean {
    const job = this.jobs.find(j => j.id === potentialDepId);
    if (!job || !job.dependencies) return false;
    
    return job.dependencies.some((dep: any) => 
      dep.id === jobId || this.isDependencyOf(jobId, dep.id)
    );
  }

  autoLayout() {
    this.calculateLayout();
    this.buildConnections();
  }

  zoomIn() {
    this.zoomLevel = Math.min(2, this.zoomLevel + 0.1);
  }

  zoomOut() {
    this.zoomLevel = Math.max(0.5, this.zoomLevel - 0.1);
  }

  resetZoom() {
    this.zoomLevel = 1;
  }
}