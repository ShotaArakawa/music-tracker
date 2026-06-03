package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.SongDeadline;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.ScheduleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制作スケジュール（カレンダー）画面と、そのイベント取得 API。
 */
@Controller
public class CalendarController {

    private final ScheduleService scheduleService;

    public CalendarController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 月間カレンダー画面。 */
    @GetMapping("/calendar")
    public String calendar() {
        return "songs/calendar";
    }

    /** カレンダーに表示する納期イベントを JSON で返す（ログインユーザーの曲のみ）。 */
    @GetMapping("/api/calendar/events")
    @ResponseBody
    public List<Map<String, Object>> events(@AuthenticationPrincipal CustomUserDetails principal) {
        return scheduleService.findAllWithDeadline(principal.getUser()).stream()
                .map(this::toEvent)
                .toList();
    }

    private Map<String, Object> toEvent(SongDeadline d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("songId", d.id());
        m.put("title", d.title());
        m.put("date", d.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
        m.put("raw", d.deadlineRaw());
        m.put("daysUntil", d.daysUntil());
        m.put("overdue", d.isOverdue());
        return m;
    }
}
