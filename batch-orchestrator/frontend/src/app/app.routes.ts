import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { 
    path: 'dashboard', 
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  { 
    path: 'jobs', 
    loadComponent: () => import('./components/jobs/jobs.component').then(m => m.JobsComponent)
  },
  { 
    path: 'executions', 
    loadComponent: () => import('./components/executions/executions.component').then(m => m.ExecutionsComponent)
  },
  { 
    path: 'monitoring', 
    loadComponent: () => import('./components/monitoring/monitoring.component').then(m => m.MonitoringComponent)
  },
  { 
    path: 'dependencies', 
    loadComponent: () => import('./components/job-dependencies/job-dependencies.component').then(m => m.JobDependenciesComponent)
  }
];