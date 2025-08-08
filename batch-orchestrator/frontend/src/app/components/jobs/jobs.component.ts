import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JobService } from '../../services/job.service';
import { JobEditModalComponent } from '../job-edit-modal/job-edit-modal.component';

@Component({
  selector: 'app-jobs',
  standalone: true,
  imports: [CommonModule, JobEditModalComponent],
  template: `
    <div class="jobs">
      <div class="header">
        <h2>Jobs</h2>
        <button class="btn-primary" (click)="createJob()">Create New Job</button>
      </div>
      
      <div class="jobs-table">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Type</th>
              <th>Schedule</th>
              <th>Dependencies</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let job of jobs">
              <td>{{ job.id }}</td>
              <td>
                {{ job.name }}
                <span class="dependency-indicator" *ngIf="job.dependencies && job.dependencies.length > 0" 
                      [title]="'Depends on: ' + getDependencyNames(job)">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 3.5a.5.5 0 0 0-1 0V8H3.5a.5.5 0 0 0 0 1H7v4.5a.5.5 0 0 0 1 0V9h3.5a.5.5 0 0 0 0-1H8V3.5z"/>
                  </svg>
                  {{ job.dependencies.length }}
                </span>
              </td>
              <td>{{ job.type }}</td>
              <td>{{ job.schedule }}</td>
              <td>
                <div class="dependency-info">
                  <span *ngIf="!job.dependencies || job.dependencies.length === 0" class="no-deps">None</span>
                  <span *ngIf="job.dependencies && job.dependencies.length > 0" class="has-deps">
                    {{ job.dependencies.length }} dep{{ job.dependencies.length > 1 ? 's' : '' }}
                  </span>
                </div>
              </td>
              <td><span class="status" [class]="job.status">{{ job.status }}</span></td>
              <td>
                <button class="btn-sm" (click)="executeJob(job.id)">Execute</button>
                <button class="btn-sm" (click)="editJob(job)">Edit</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <!-- Edit Modal -->
      <app-job-edit-modal
        [job]="selectedJob"
        [isOpen]="isEditModalOpen"
        (closeEvent)="closeEditModal()"
        (saveEvent)="saveJob($event)">
      </app-job-edit-modal>
    </div>
  `,
  styles: [`
    .jobs {
      padding: 1rem;
    }
    
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
    }
    
    .btn-primary {
      background-color: #3498db;
      color: white;
      border: none;
      padding: 0.75rem 1.5rem;
      border-radius: 4px;
      cursor: pointer;
    }
    
    .btn-primary:hover {
      background-color: #2980b9;
    }
    
    .jobs-table {
      background: white;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    table {
      width: 100%;
      border-collapse: collapse;
    }
    
    th {
      background-color: #f8f9fa;
      padding: 1rem;
      text-align: left;
      font-weight: 600;
    }
    
    td {
      padding: 1rem;
      border-top: 1px solid #dee2e6;
    }
    
    .status {
      padding: 0.25rem 0.75rem;
      border-radius: 12px;
      font-size: 0.875rem;
    }
    
    .status.active {
      background-color: #d4edda;
      color: #155724;
    }
    
    .status.inactive {
      background-color: #f8d7da;
      color: #721c24;
    }
    
    .btn-sm {
      padding: 0.25rem 0.75rem;
      margin-right: 0.5rem;
      border: 1px solid #dee2e6;
      background: white;
      border-radius: 4px;
      cursor: pointer;
    }
    
    .btn-sm:hover {
      background-color: #f8f9fa;
    }
    
    .dependency-indicator {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      margin-left: 0.5rem;
      padding: 0.125rem 0.5rem;
      background-color: #e3f2fd;
      color: #1976d2;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: help;
    }
    
    .dependency-indicator svg {
      opacity: 0.7;
    }
    
    .dependency-info {
      font-size: 0.875rem;
    }
    
    .no-deps {
      color: #999;
    }
    
    .has-deps {
      color: #1976d2;
      font-weight: 500;
    }
  `]
})
export class JobsComponent implements OnInit {
  jobs: any[] = [];
  selectedJob: any = null;
  isEditModalOpen: boolean = false;

  constructor(private jobService: JobService) {}

  ngOnInit() {
    this.loadJobs();
  }

  loadJobs() {
    this.jobService.getJobsWithDependencies().subscribe(
      jobs => this.jobs = jobs,
      error => console.error('Error loading jobs:', error)
    );
  }
  
  getDependencyNames(job: any): string {
    if (!job.dependencies || job.dependencies.length === 0) {
      return '';
    }
    return job.dependencies.map((dep: any) => dep.name).join(', ');
  }

  createJob() {
    // Open modal with empty job for creation
    this.selectedJob = {
      name: '',
      type: 'BATCH',
      schedule: '',
      command: '',
      description: '',
      status: 'active',
      active: true,
      maxRetries: 3,
      timeoutMinutes: 120,
      priority: 5,
      dependencies: []
    };
    this.isEditModalOpen = true;
  }

  executeJob(id: number) {
    console.log('Executing job:', id);
    this.jobService.executeJob(id).subscribe(
      response => {
        console.log('Job executed successfully:', response);
        alert('Job execution started successfully!');
      },
      error => {
        console.error('Error executing job:', error);
        alert('Failed to execute job. Please try again.');
      }
    );
  }

  editJob(job: any) {
    this.selectedJob = job;
    this.isEditModalOpen = true;
  }
  
  closeEditModal() {
    this.isEditModalOpen = false;
    this.selectedJob = null;
  }
  
  saveJob(job: any) {
    if (job.id) {
      // Update existing job
      this.jobService.updateJob(job.id, job).subscribe(
        response => {
          console.log('Job updated successfully:', response);
          this.loadJobs(); // Reload jobs list
          alert('Job updated successfully!');
        },
        error => {
          console.error('Error updating job:', error);
          alert('Failed to update job. Please try again.');
        }
      );
    } else {
      // Create new job
      this.jobService.createJob(job).subscribe(
        response => {
          console.log('Job created successfully:', response);
          this.loadJobs(); // Reload jobs list
          alert('Job created successfully!');
        },
        error => {
          console.error('Error creating job:', error);
          alert('Failed to create job. Please try again.');
        }
      );
    }
  }
}