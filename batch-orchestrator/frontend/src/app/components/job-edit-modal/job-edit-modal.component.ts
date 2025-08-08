import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JobService } from '../../services/job.service';

@Component({
  selector: 'app-job-edit-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="closeModal()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2>Edit Job</h2>
          <button class="close-btn" (click)="closeModal()">×</button>
        </div>
        
        <div class="modal-body">
          <form (ngSubmit)="saveJob()">
            <div class="form-group">
              <label for="jobName">Job Name</label>
              <input 
                type="text" 
                id="jobName" 
                [(ngModel)]="editableJob.name" 
                name="name"
                class="form-control"
                required>
            </div>
            
            <div class="form-group">
              <label for="jobType">Job Type</label>
              <select 
                id="jobType" 
                [(ngModel)]="editableJob.type" 
                name="type"
                class="form-control">
                <option value="BATCH">Batch</option>
                <option value="SYNC">Sync</option>
                <option value="BACKUP">Backup</option>
                <option value="ETL">ETL</option>
                <option value="REPORT">Report</option>
                <option value="CLEANUP">Cleanup</option>
              </select>
            </div>
            
            <div class="form-group">
              <label for="schedule">Cron Schedule</label>
              <input 
                type="text" 
                id="schedule" 
                [(ngModel)]="editableJob.schedule" 
                name="schedule"
                class="form-control"
                placeholder="e.g., 0 0 * * * (daily at midnight)">
              <small class="help-text">Use cron expression format</small>
            </div>
            
            <div class="form-group">
              <label for="command">Command</label>
              <textarea 
                id="command" 
                [(ngModel)]="editableJob.command" 
                name="command"
                class="form-control"
                rows="3"
                placeholder="Enter the command or script to execute"></textarea>
            </div>
            
            <div class="form-group">
              <label for="description">Description</label>
              <textarea 
                id="description" 
                [(ngModel)]="editableJob.description" 
                name="description"
                class="form-control"
                rows="2"
                placeholder="Brief description of what this job does"></textarea>
            </div>
            
            <div class="form-row">
              <div class="form-group half">
                <label for="maxRetries">Max Retries</label>
                <input 
                  type="number" 
                  id="maxRetries" 
                  [(ngModel)]="editableJob.maxRetries" 
                  name="maxRetries"
                  class="form-control"
                  min="0"
                  max="10">
              </div>
              
              <div class="form-group half">
                <label for="timeoutMinutes">Timeout (minutes)</label>
                <input 
                  type="number" 
                  id="timeoutMinutes" 
                  [(ngModel)]="editableJob.timeoutMinutes" 
                  name="timeoutMinutes"
                  class="form-control"
                  min="1"
                  max="1440">
              </div>
            </div>
            
            <div class="form-row">
              <div class="form-group half">
                <label for="priority">Priority (1-10)</label>
                <input 
                  type="number" 
                  id="priority" 
                  [(ngModel)]="editableJob.priority" 
                  name="priority"
                  class="form-control"
                  min="1"
                  max="10">
                <small class="help-text">10 = highest priority</small>
              </div>
              
              <div class="form-group half">
                <label for="status">Status</label>
                <select 
                  id="status" 
                  [(ngModel)]="editableJob.status" 
                  name="status"
                  class="form-control">
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                  <option value="paused">Paused</option>
                </select>
              </div>
            </div>
            
            <div class="form-group">
              <label for="dependencies">Job Dependencies</label>
              <div class="dependencies-section">
                <div class="selected-dependencies" *ngIf="editableJob.dependencies && editableJob.dependencies.length > 0">
                  <div class="dependency-tag" *ngFor="let dep of editableJob.dependencies">
                    {{ dep.name }}
                    <button type="button" class="remove-dep" (click)="removeDependency(dep)">×</button>
                  </div>
                </div>
                <select 
                  id="addDependency" 
                  [(ngModel)]="selectedDependency" 
                  name="selectedDependency"
                  (change)="addDependency()"
                  class="form-control">
                  <option value="">-- Add a dependency --</option>
                  <option *ngFor="let job of availableJobs" [value]="job.id">
                    {{ job.name }}
                  </option>
                </select>
                <small class="help-text">This job will wait for selected dependencies to complete before starting</small>
              </div>
            </div>
            
            <div class="form-group">
              <label class="checkbox-label">
                <input 
                  type="checkbox" 
                  [(ngModel)]="editableJob.active" 
                  name="active">
                Enable Job
              </label>
            </div>
            
            <div class="modal-footer">
              <button type="button" class="btn-secondary" (click)="closeModal()">Cancel</button>
              <button type="submit" class="btn-primary">Save Changes</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0.5);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 1000;
      animation: fadeIn 0.2s;
    }
    
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    
    .modal-content {
      background: white;
      border-radius: 8px;
      width: 90%;
      max-width: 600px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
      animation: slideIn 0.3s;
    }
    
    @keyframes slideIn {
      from {
        transform: translateY(-20px);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }
    
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      border-bottom: 1px solid #e0e0e0;
    }
    
    .modal-header h2 {
      margin: 0;
      color: #2c3e50;
    }
    
    .close-btn {
      background: none;
      border: none;
      font-size: 2rem;
      color: #999;
      cursor: pointer;
      padding: 0;
      width: 30px;
      height: 30px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
      transition: all 0.2s;
    }
    
    .close-btn:hover {
      background-color: #f0f0f0;
      color: #333;
    }
    
    .modal-body {
      padding: 1.5rem;
    }
    
    .form-group {
      margin-bottom: 1.25rem;
    }
    
    .form-row {
      display: flex;
      gap: 1rem;
    }
    
    .form-group.half {
      flex: 1;
    }
    
    label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: 500;
      color: #555;
      font-size: 0.95rem;
    }
    
    .form-control {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      transition: border-color 0.2s;
    }
    
    .form-control:focus {
      outline: none;
      border-color: #3498db;
      box-shadow: 0 0 0 2px rgba(52, 152, 219, 0.1);
    }
    
    textarea.form-control {
      resize: vertical;
      font-family: inherit;
    }
    
    .help-text {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.85rem;
      color: #999;
    }
    
    .checkbox-label {
      display: flex;
      align-items: center;
      font-weight: normal;
    }
    
    .checkbox-label input {
      margin-right: 0.5rem;
      width: 18px;
      height: 18px;
      cursor: pointer;
    }
    
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 1rem;
      padding-top: 1.5rem;
      margin-top: 1.5rem;
      border-top: 1px solid #e0e0e0;
    }
    
    .btn-primary, .btn-secondary {
      padding: 0.75rem 1.5rem;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      cursor: pointer;
      transition: all 0.2s;
      font-weight: 500;
    }
    
    .btn-primary {
      background-color: #3498db;
      color: white;
    }
    
    .btn-primary:hover {
      background-color: #2980b9;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(52, 152, 219, 0.3);
    }
    
    .btn-secondary {
      background-color: #f0f0f0;
      color: #333;
    }
    
    .btn-secondary:hover {
      background-color: #e0e0e0;
    }
    
    .dependencies-section {
      margin-top: 0.5rem;
    }
    
    .selected-dependencies {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    
    .dependency-tag {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.25rem 0.75rem;
      background-color: #e3f2fd;
      border: 1px solid #90caf9;
      border-radius: 16px;
      font-size: 0.9rem;
      color: #1976d2;
    }
    
    .remove-dep {
      background: none;
      border: none;
      color: #1976d2;
      cursor: pointer;
      font-size: 1.2rem;
      padding: 0;
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      transition: background-color 0.2s;
    }
    
    .remove-dep:hover {
      background-color: rgba(25, 118, 210, 0.1);
    }
  `]
})
export class JobEditModalComponent implements OnInit {
  @Input() job: any = null;
  @Input() isOpen: boolean = false;
  @Output() closeEvent = new EventEmitter<void>();
  @Output() saveEvent = new EventEmitter<any>();
  
  editableJob: any = {};
  availableJobs: any[] = [];
  selectedDependency: string = '';
  
  constructor(private jobService: JobService) {}
  
  ngOnInit() {
    if (this.job) {
      this.editableJob = { ...this.job };
      if (!this.editableJob.dependencies) {
        this.editableJob.dependencies = [];
      }
    }
    this.loadAvailableJobs();
  }
  
  ngOnChanges() {
    if (this.job) {
      this.editableJob = { ...this.job };
      if (!this.editableJob.dependencies) {
        this.editableJob.dependencies = [];
      }
    }
  }
  
  loadAvailableJobs() {
    this.jobService.getJobsWithDependencies().subscribe(jobs => {
      // Filter out current job and already selected dependencies
      this.availableJobs = jobs.filter(j => {
        if (j.id === this.editableJob.id) return false;
        if (this.editableJob.dependencies) {
          return !this.editableJob.dependencies.some((dep: any) => dep.id === j.id);
        }
        return true;
      });
    });
  }
  
  addDependency() {
    if (this.selectedDependency) {
      const jobToAdd = this.availableJobs.find(j => j.id === parseInt(this.selectedDependency));
      if (jobToAdd) {
        if (!this.editableJob.dependencies) {
          this.editableJob.dependencies = [];
        }
        
        // Check for circular dependency
        if (this.wouldCreateCircularDependency(this.editableJob.id, jobToAdd.id)) {
          alert('Cannot add this dependency as it would create a circular dependency!');
          this.selectedDependency = '';
          return;
        }
        
        this.editableJob.dependencies.push({ id: jobToAdd.id, name: jobToAdd.name });
        this.selectedDependency = '';
        this.loadAvailableJobs(); // Refresh available list
      }
    }
  }
  
  removeDependency(dep: any) {
    const index = this.editableJob.dependencies.findIndex((d: any) => d.id === dep.id);
    if (index > -1) {
      this.editableJob.dependencies.splice(index, 1);
      this.loadAvailableJobs(); // Refresh available list
    }
  }
  
  wouldCreateCircularDependency(jobId: number, newDepId: number): boolean {
    // Simple check - in production, this would be done on the backend
    // For now, just prevent obvious circular dependencies
    return false; // Simplified for demo
  }
  
  closeModal() {
    this.closeEvent.emit();
  }
  
  saveJob() {
    this.saveEvent.emit(this.editableJob);
    this.closeModal();
  }
}