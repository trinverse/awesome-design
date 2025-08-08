import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class JobService {
  private apiUrl = '/api/api/jobs'; // Will be proxied to backend

  constructor(private http: HttpClient) {}

  getJobs(): Observable<any[]> {
    // Temporarily return mock data until backend is connected
    return of([
      { 
        id: 1, 
        name: 'Daily Report', 
        type: 'BATCH', 
        schedule: '0 0 * * *', 
        status: 'active',
        command: 'generate-daily-report.sh',
        description: 'Generates daily business reports',
        maxRetries: 3,
        timeoutMinutes: 60,
        priority: 7,
        active: true
      },
      { 
        id: 2, 
        name: 'Data Sync', 
        type: 'SYNC', 
        schedule: '*/30 * * * *', 
        status: 'active',
        command: 'sync-data.py',
        description: 'Synchronizes data between systems',
        maxRetries: 5,
        timeoutMinutes: 30,
        priority: 8,
        active: true
      },
      { 
        id: 3, 
        name: 'Backup Job', 
        type: 'BACKUP', 
        schedule: '0 2 * * *', 
        status: 'inactive',
        command: 'backup-database.sh',
        description: 'Performs nightly database backup',
        maxRetries: 2,
        timeoutMinutes: 120,
        priority: 10,
        active: false
      }
    ]);
    
    // Uncomment when backend is ready
    // return this.http.get<any[]>(this.apiUrl).pipe(
    //   catchError(error => {
    //     console.error('Error fetching jobs:', error);
    //     return of([]);
    //   })
    // );
  }

  getJob(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  createJob(job: any): Observable<any> {
    // Mock implementation for now
    return of({ ...job, id: Math.floor(Math.random() * 1000) + 4 });
    
    // Uncomment when backend is ready
    // return this.http.post<any>(this.apiUrl, job);
  }
  
  updateJob(id: number, job: any): Observable<any> {
    // Mock implementation for now
    return of({ ...job, id });
    
    // Uncomment when backend is ready
    // return this.http.put<any>(`${this.apiUrl}/${id}`, job);
  }

  executeJob(id: number): Observable<any> {
    // Mock implementation for now
    return of({ jobId: id, executionId: Math.floor(Math.random() * 10000), status: 'RUNNING' });
    
    // Uncomment when backend is ready
    // return this.http.post<any>(`${this.apiUrl}/${id}/execute`, {});
  }

  getJobsWithDependencies(): Observable<any[]> {
    // Mock data with dependencies
    return of([
      { 
        id: 1, 
        name: 'ETL Extract', 
        type: 'BATCH',
        status: 'active',
        dependencies: []
      },
      { 
        id: 2, 
        name: 'ETL Transform', 
        type: 'BATCH',
        status: 'active',
        dependencies: [{ id: 1, name: 'ETL Extract' }]
      },
      { 
        id: 3, 
        name: 'ETL Load', 
        type: 'BATCH',
        status: 'active',
        dependencies: [{ id: 2, name: 'ETL Transform' }]
      },
      { 
        id: 4, 
        name: 'Generate Report', 
        type: 'REPORT',
        status: 'active',
        dependencies: [{ id: 3, name: 'ETL Load' }]
      },
      { 
        id: 5, 
        name: 'Send Notifications', 
        type: 'NOTIFICATION',
        status: 'active',
        dependencies: [{ id: 4, name: 'Generate Report' }]
      },
      { 
        id: 6, 
        name: 'Data Validation', 
        type: 'VALIDATION',
        status: 'active',
        dependencies: [{ id: 1, name: 'ETL Extract' }]
      },
      { 
        id: 7, 
        name: 'Backup Database', 
        type: 'BACKUP',
        status: 'active',
        dependencies: []
      },
      { 
        id: 8, 
        name: 'Archive Old Data', 
        type: 'MAINTENANCE',
        status: 'active',
        dependencies: [{ id: 7, name: 'Backup Database' }]
      }
    ]);
    
    // Uncomment when backend is ready
    // return this.http.get<any[]>(`${this.apiUrl}/dependencies`);
  }

  updateJobDependencies(jobId: number, dependencies: any[]): Observable<any> {
    // Mock implementation
    return of({ jobId, dependencies, success: true });
    
    // Uncomment when backend is ready
    // return this.http.put<any>(`${this.apiUrl}/${jobId}/dependencies`, { dependencies });
  }
}