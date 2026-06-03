package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.ReminderSummary;
import com.portfolio.musictracker.dto.SongDeadline;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.ScheduleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * リマインダーの手動実行用エンドポイント（動作確認用）。
 * ログインユーザー自身の納期アラートのみを返す（他ユーザーの情報は含めない）。
 */
@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ScheduleService scheduleService;

    public ReminderController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** ログインユーザーのリマインダー対象を集計して返す。 */
    @GetMapping("/run")
    public ReminderSummary run(@AuthenticationPrincipal CustomUserDetails principal) {
        List<SongDeadline> alerts = scheduleService.findAlerts(principal.getUser());
        long overdue = alerts.stream().filter(SongDeadline::isOverdue).count();
        long soon = alerts.size() - overdue;
        return new ReminderSummary(overdue, soon, alerts);
    }
}
