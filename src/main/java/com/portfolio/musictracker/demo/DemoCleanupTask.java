package com.portfolio.musictracker.demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 期限切れのお試しアカウントを定期的に削除する（起動1分後から30分ごと）。
 */
@Component
public class DemoCleanupTask {

    private final DemoAccountService demoAccountService;

    public DemoCleanupTask(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 30 * 60_000)
    public void deleteExpired() {
        demoAccountService.deleteExpired();
    }
}
