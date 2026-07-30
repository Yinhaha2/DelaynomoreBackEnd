package org.example.schoolshop.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.schoolshop.config.SchoolShopProperties;
import org.example.schoolshop.service.TaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskScheduledJob {

    private final TaskService taskService;
    private final SchoolShopProperties properties;

    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyJobs() {
        if (!properties.getTaskJob().isEnabled()) {
            return;
        }
        int confirmed = taskService.autoConfirmExpiredTasks();
        if (confirmed > 0) {
            log.info("自动验收任务 {} 条", confirmed);
        }
        if (properties.getTaskJob().isCancelExpiredRecruiting()) {
            int cancelled = taskService.cancelExpiredRecruitingTasks();
            if (cancelled > 0) {
                log.info("超时取消招募任务 {} 条", cancelled);
            }
        }
    }
}
