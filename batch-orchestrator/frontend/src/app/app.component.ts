import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar">
      <div class="container">
        <h1 class="nav-title">Batch Orchestrator</h1>
        <ul class="nav-links">
          <li><a routerLink="/dashboard" routerLinkActive="active">Dashboard</a></li>
          <li><a routerLink="/jobs" routerLinkActive="active">Jobs</a></li>
          <li><a routerLink="/executions" routerLinkActive="active">Executions</a></li>
          <li><a routerLink="/monitoring" routerLinkActive="active">Monitoring</a></li>
          <li><a routerLink="/dependencies" routerLinkActive="active">Dependencies</a></li>
        </ul>
      </div>
    </nav>
    <main class="main-content">
      <div class="container">
        <router-outlet></router-outlet>
      </div>
    </main>
  `,
  styles: [`
    .navbar {
      background-color: #2c3e50;
      color: white;
      padding: 1rem 0;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .nav-title {
      display: inline-block;
      font-size: 1.5rem;
      margin: 0;
    }
    
    .nav-links {
      display: inline-block;
      list-style: none;
      margin-left: 2rem;
    }
    
    .nav-links li {
      display: inline-block;
      margin-right: 1.5rem;
    }
    
    .nav-links a {
      color: white;
      text-decoration: none;
      padding: 0.5rem 1rem;
      border-radius: 4px;
      transition: background-color 0.3s;
    }
    
    .nav-links a:hover {
      background-color: rgba(255,255,255,0.1);
    }
    
    .nav-links a.active {
      background-color: #34495e;
    }
    
    .main-content {
      padding: 2rem 0;
      min-height: calc(100vh - 80px);
    }
  `]
})
export class AppComponent {
  title = 'Batch Orchestrator';
}