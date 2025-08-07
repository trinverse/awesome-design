#!/usr/bin/env python3
"""
Autosys to Batch Orchestrator Migration Script
Converts Autosys JIL files to SQL insert statements for the new system
"""

import re
import json
import sys
import argparse
from datetime import datetime
from typing import Dict, List, Any

class AutosysMigrator:
    """Migrates Autosys job definitions to the new batch orchestrator system."""
    
    def __init__(self):
        self.jobs = []
        self.dependencies = []
        self.schedules = []
        self.boxes = {}
        self.job_id_map = {}
        self.current_id = 1000  # Starting ID for migrated jobs
        
    def parse_jil_file(self, filename: str) -> List[Dict[str, Any]]:
        """Parse Autosys JIL file and extract job definitions."""
        jobs = []
        current_job = None
        
        with open(filename, 'r') as f:
            lines = f.readlines()
        
        for line in lines:
            line = line.strip()
            
            # Skip comments and empty lines
            if not line or line.startswith('/*'):
                continue
            
            # New job definition
            if line.startswith('insert_job:'):
                if current_job:
                    jobs.append(current_job)
                
                parts = line.split(':')
                job_name = parts[1].strip().split()[0]
                job_type = parts[2].strip() if len(parts) > 2 else 'CMD'
                
                current_job = {
                    'name': job_name,
                    'type': self._map_job_type(job_type),
                    'attributes': {}
                }
            
            # Job attributes
            elif current_job and ':' in line:
                key, value = line.split(':', 1)
                key = key.strip()
                value = value.strip().strip('"')
                current_job['attributes'][key] = value
        
        # Add last job
        if current_job:
            jobs.append(current_job)
        
        return jobs
    
    def _map_job_type(self, autosys_type: str) -> str:
        """Map Autosys job type to new system job type."""
        type_mapping = {
            'CMD': 'SHELL',
            'BOX': 'CHAIN',
            'FW': 'FILE_WATCHER',
            'SCRIPT': 'SHELL',
            'SQL': 'STORED_PROC'
        }
        return type_mapping.get(autosys_type.upper(), 'SHELL')
    
    def convert_job(self, autosys_job: Dict[str, Any]) -> Dict[str, Any]:
        """Convert Autosys job to new system format."""
        job_id = self.current_id
        self.job_id_map[autosys_job['name']] = job_id
        self.current_id += 1
        
        attrs = autosys_job['attributes']
        
        # Map common attributes
        new_job = {
            'job_id': job_id,
            'job_name': autosys_job['name'],
            'job_group': attrs.get('box_name', 'DEFAULT'),
            'job_type': autosys_job['type'],
            'description': attrs.get('description', f"Migrated from Autosys: {autosys_job['name']}"),
            'is_active': 1,
            'priority': self._map_priority(attrs.get('priority', '0')),
            'max_retry_count': int(attrs.get('n_retrys', '0')),
            'retry_interval_seconds': 300,
            'timeout_minutes': int(attrs.get('term_run_time', '60')),
            'alert_on_failure': 1 if attrs.get('alarm_if_fail') == 'y' else 0,
            'critical_job': 1 if attrs.get('job_terminator') == 'y' else 0,
            'created_by': 'MIGRATION',
            'modified_by': 'MIGRATION'
        }
        
        # Map job-specific attributes
        if autosys_job['type'] == 'SHELL':
            new_job['script_path'] = attrs.get('command', '')
        elif autosys_job['type'] == 'FILE_WATCHER':
            self._create_file_watcher_config(job_id, attrs)
        
        # Handle scheduling
        if 'start_times' in attrs or 'run_calendar' in attrs:
            self._create_schedule(job_id, attrs)
        
        # Handle dependencies
        if 'condition' in attrs:
            self._parse_dependencies(job_id, attrs['condition'])
        
        return new_job
    
    def _map_priority(self, autosys_priority: str) -> int:
        """Map Autosys priority to new system priority (1-10)."""
        try:
            # Autosys uses 0-100, we use 1-10
            priority = int(autosys_priority)
            return min(10, max(1, priority // 10))
        except:
            return 5  # Default priority
    
    def _create_schedule(self, job_id: int, attrs: Dict[str, str]):
        """Create schedule from Autosys attributes."""
        schedule = {
            'job_id': job_id,
            'schedule_name': f"Schedule_{job_id}",
            'created_by': 'MIGRATION',
            'modified_by': 'MIGRATION'
        }
        
        # Parse start times (cron format)
        if 'start_times' in attrs:
            cron_expr = self._convert_to_cron(attrs['start_times'])
            schedule['schedule_type'] = 'CRON'
            schedule['cron_expression'] = cron_expr
        
        # Handle run calendar
        if 'run_calendar' in attrs:
            schedule['calendar_name'] = attrs['run_calendar']
        
        # Handle date conditions
        if 'date_conditions' in attrs:
            schedule['date_conditions'] = attrs['date_conditions']
        
        self.schedules.append(schedule)
    
    def _convert_to_cron(self, start_times: str) -> str:
        """Convert Autosys start_times to cron expression."""
        # Simple conversion - this would need more sophisticated parsing
        # for complex Autosys time specifications
        
        # Example: "10:30" -> "30 10 * * *"
        if ':' in start_times:
            parts = start_times.split(':')
            if len(parts) == 2:
                hour = parts[0].strip()
                minute = parts[1].strip()
                return f"{minute} {hour} * * *"
        
        # Default to daily at midnight
        return "0 0 * * *"
    
    def _parse_dependencies(self, job_id: int, condition: str):
        """Parse Autosys condition string and create dependencies."""
        # Parse conditions like: "success(job1) and success(job2)"
        # or "s(job1,12.00) and s(job2)"
        
        pattern = r'(success|failure|done|terminated|notrunning|exitcode)\(([^)]+)\)'
        matches = re.findall(pattern, condition, re.IGNORECASE)
        
        for match in matches:
            dep_type = match[0].lower()
            dep_job = match[1].split(',')[0].strip()  # Get job name, ignore other params
            
            # Map dependency type
            if dep_type in ['success', 's']:
                dependency_type = 'SUCCESS'
            elif dep_type in ['failure', 'f']:
                dependency_type = 'FAILURE'
            else:
                dependency_type = 'COMPLETION'
            
            self.dependencies.append({
                'job_id': job_id,
                'dependent_job_name': dep_job,  # Will map to ID later
                'dependency_type': dependency_type,
                'created_by': 'MIGRATION'
            })
    
    def _create_file_watcher_config(self, job_id: int, attrs: Dict[str, str]):
        """Create file watcher configuration from Autosys attributes."""
        config = {
            'job_id': job_id,
            'watch_directory': attrs.get('watch_file', '').rsplit('/', 1)[0] or '/tmp',
            'file_pattern': attrs.get('watch_file', '').rsplit('/', 1)[-1] or '*',
            'stable_time_seconds': int(attrs.get('watch_file_min_size', '5')),
            'created_date': datetime.now().isoformat()
        }
        
        # This would be inserted into file_watcher_config table
        return config
    
    def generate_sql(self, output_file: str):
        """Generate SQL insert statements for migration."""
        with open(output_file, 'w') as f:
            f.write("-- Autosys Migration SQL Script\n")
            f.write(f"-- Generated: {datetime.now().isoformat()}\n")
            f.write("-- =============================================\n\n")
            
            f.write("BEGIN TRANSACTION;\n\n")
            
            # Insert job definitions
            f.write("-- Job Definitions\n")
            for job in self.jobs:
                sql = self._generate_job_insert(job)
                f.write(sql + "\n")
            
            f.write("\n-- Job Schedules\n")
            for schedule in self.schedules:
                sql = self._generate_schedule_insert(schedule)
                f.write(sql + "\n")
            
            f.write("\n-- Job Dependencies\n")
            for dep in self.dependencies:
                # Map job names to IDs
                if dep['dependent_job_name'] in self.job_id_map:
                    dep['dependent_job_id'] = self.job_id_map[dep['dependent_job_name']]
                    sql = self._generate_dependency_insert(dep)
                    f.write(sql + "\n")
            
            f.write("\nCOMMIT;\n")
    
    def _generate_job_insert(self, job: Dict[str, Any]) -> str:
        """Generate SQL insert for job definition."""
        columns = ', '.join(job.keys())
        values = []
        
        for key, value in job.items():
            if isinstance(value, str):
                values.append(f"'{value.replace(\"'\", \"''\")}'")
            elif value is None:
                values.append('NULL')
            else:
                values.append(str(value))
        
        values_str = ', '.join(values)
        
        return f"INSERT INTO job_definitions ({columns}) VALUES ({values_str});"
    
    def _generate_schedule_insert(self, schedule: Dict[str, Any]) -> str:
        """Generate SQL insert for job schedule."""
        columns = ', '.join(schedule.keys())
        values = []
        
        for key, value in schedule.items():
            if isinstance(value, str):
                values.append(f"'{value.replace(\"'\", \"''\")}'")
            elif value is None:
                values.append('NULL')
            else:
                values.append(str(value))
        
        values_str = ', '.join(values)
        
        return f"INSERT INTO job_schedules ({columns}) VALUES ({values_str});"
    
    def _generate_dependency_insert(self, dep: Dict[str, Any]) -> str:
        """Generate SQL insert for job dependency."""
        return (f"INSERT INTO job_dependencies (job_id, dependent_job_id, dependency_type, created_by) "
                f"VALUES ({dep['job_id']}, {dep['dependent_job_id']}, '{dep['dependency_type']}', "
                f"'{dep['created_by']}');")
    
    def migrate(self, jil_file: str, output_file: str):
        """Main migration process."""
        print(f"Starting migration from {jil_file}")
        
        # Parse JIL file
        autosys_jobs = self.parse_jil_file(jil_file)
        print(f"Found {len(autosys_jobs)} jobs in JIL file")
        
        # Convert jobs
        for autosys_job in autosys_jobs:
            if autosys_job['type'] != 'CHAIN':  # Skip boxes for now
                new_job = self.convert_job(autosys_job)
                self.jobs.append(new_job)
        
        print(f"Converted {len(self.jobs)} jobs")
        print(f"Created {len(self.schedules)} schedules")
        print(f"Created {len(self.dependencies)} dependencies")
        
        # Generate SQL
        self.generate_sql(output_file)
        print(f"Migration SQL written to {output_file}")
        
        # Generate summary report
        self._generate_summary_report()
    
    def _generate_summary_report(self):
        """Generate migration summary report."""
        report = {
            'migration_date': datetime.now().isoformat(),
            'total_jobs': len(self.jobs),
            'total_schedules': len(self.schedules),
            'total_dependencies': len(self.dependencies),
            'job_types': {},
            'job_groups': {}
        }
        
        # Count job types
        for job in self.jobs:
            job_type = job['job_type']
            report['job_types'][job_type] = report['job_types'].get(job_type, 0) + 1
        
        # Count job groups
        for job in self.jobs:
            group = job['job_group']
            report['job_groups'][group] = report['job_groups'].get(group, 0) + 1
        
        # Write report
        with open('migration_report.json', 'w') as f:
            json.dump(report, f, indent=2)
        
        print("\nMigration Summary:")
        print(f"  Total Jobs: {report['total_jobs']}")
        print(f"  Total Schedules: {report['total_schedules']}")
        print(f"  Total Dependencies: {report['total_dependencies']}")
        print("\nJob Types:")
        for jtype, count in report['job_types'].items():
            print(f"  {jtype}: {count}")
        print("\nTop Job Groups:")
        for group, count in sorted(report['job_groups'].items(), key=lambda x: x[1], reverse=True)[:5]:
            print(f"  {group}: {count}")


def main():
    parser = argparse.ArgumentParser(description='Migrate Autosys jobs to Batch Orchestrator')
    parser.add_argument('jil_file', help='Input JIL file path')
    parser.add_argument('-o', '--output', default='migration.sql', help='Output SQL file')
    parser.add_argument('--validate', action='store_true', help='Validate migration only')
    
    args = parser.parse_args()
    
    migrator = AutosysMigrator()
    
    try:
        migrator.migrate(args.jil_file, args.output)
        print("\nMigration completed successfully!")
        
    except Exception as e:
        print(f"Migration failed: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()