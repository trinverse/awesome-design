package com.bank.batchorchestrator.scheduler;

import com.bank.batchorchestrator.entity.Job;
import com.bank.batchorchestrator.repository.JobRepository;
import com.bank.batchorchestrator.service.OrchestratorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduler {
    private final Scheduler scheduler;
    private final JobRepository jobRepository;
    private final OrchestratorService orchestratorService;
    
    private static final String JOB_GROUP = "batch-jobs";
    
    @PostConstruct
    public void initializeScheduledJobs() {
        try {
            log.info("Initializing job scheduler...");
            
            // Clear existing jobs
            clearAllJobs();
            
            // Load and schedule active jobs
            List<Job> scheduledJobs = jobRepository.findScheduledJobs();
            
            for (Job job : scheduledJobs) {
                if (job.getSchedule() != null && !job.getSchedule().trim().isEmpty()) {
                    scheduleJob(job);
                }
            }
            
            log.info("Scheduled {} jobs", scheduledJobs.size());
            
        } catch (Exception e) {
            log.error("Error initializing scheduled jobs", e);
        }
    }
    
    public void scheduleJob(Job job) throws SchedulerException {
        if (!job.getActive() || job.getSchedule() == null || job.getSchedule().trim().isEmpty()) {
            log.warn("Cannot schedule inactive job or job without schedule: {}", job.getJobName());
            return;
        }
        
        JobDetail jobDetail = buildJobDetail(job);
        Trigger trigger = buildTrigger(job);
        
        if (scheduler.checkExists(jobDetail.getKey())) {
            scheduler.deleteJob(jobDetail.getKey());
        }
        
        scheduler.scheduleJob(jobDetail, trigger);
        
        log.info("Scheduled job: {} with cron expression: {}", job.getJobName(), job.getSchedule());
    }
    
    public void unscheduleJob(Job job) throws SchedulerException {
        JobKey jobKey = new JobKey(job.getJobName(), JOB_GROUP);
        
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Unscheduled job: {}", job.getJobName());
        }
    }
    
    public void rescheduleJob(Job job) throws SchedulerException {
        unscheduleJob(job);
        
        if (job.getActive() && job.getSchedule() != null && !job.getSchedule().trim().isEmpty()) {
            scheduleJob(job);
        }
    }
    
    public void pauseJob(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);
        scheduler.pauseJob(jobKey);
        log.info("Paused job: {}", jobName);
    }
    
    public void resumeJob(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);
        scheduler.resumeJob(jobKey);
        log.info("Resumed job: {}", jobName);
    }
    
    public void triggerJobNow(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);
        scheduler.triggerJob(jobKey);
        log.info("Triggered job immediately: {}", jobName);
    }
    
    public Map<String, Object> getSchedulerStatus() throws SchedulerException {
        Map<String, Object> status = new HashMap<>();
        
        status.put("schedulerName", scheduler.getSchedulerName());
        status.put("schedulerInstanceId", scheduler.getSchedulerInstanceId());
        status.put("isStarted", scheduler.isStarted());
        status.put("isInStandbyMode", scheduler.isInStandbyMode());
        status.put("isShutdown", scheduler.isShutdown());
        
        // Get job counts
        int totalJobs = 0;
        int runningJobs = 0;
        
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                totalJobs++;
                
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : triggers) {
                    Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
                    if (triggerState == Trigger.TriggerState.BLOCKED) {
                        runningJobs++;
                    }
                }
            }
        }
        
        status.put("totalScheduledJobs", totalJobs);
        status.put("currentlyExecutingJobs", runningJobs);
        
        return status;
    }
    
    private JobDetail buildJobDetail(Job job) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobId", job.getId());
        jobDataMap.put("jobName", job.getJobName());
        
        return JobBuilder.newJob(QuartzJobExecutor.class)
                .withIdentity(job.getJobName(), JOB_GROUP)
                .withDescription(job.getDescription())
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }
    
    private Trigger buildTrigger(Job job) {
        return TriggerBuilder.newTrigger()
                .withIdentity(job.getJobName() + "-trigger", JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(job.getSchedule())
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
    
    private void clearAllJobs() throws SchedulerException {
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                scheduler.deleteJob(jobKey);
            }
        }
    }
    
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class QuartzJobExecutor implements org.quartz.Job {
        private final OrchestratorService orchestratorService;
        
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            String jobName = dataMap.getString("jobName");
            
            try {
                log.info("Executing scheduled job: {}", jobName);
                orchestratorService.submitJob(jobName, "SCHEDULER", new HashMap<>());
            } catch (Exception e) {
                log.error("Error executing scheduled job: {}", jobName, e);
                throw new JobExecutionException(e);
            }
        }
    }
}