package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.CheckinLog;
import org.example.schoolshop.mapper.CheckinLogMapper;
import org.example.schoolshop.service.CheckinService;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final CheckinLogMapper checkinLogMapper;
    private final PointsService pointsService;
    private final UserService userService;

    @Override
    public Map<String, Object> status(long userId) {
        userService.requireActiveUser(userId);
        LocalDate today = LocalDate.now();
        boolean checkedToday = checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId).eq(CheckinLog::getCheckinDate, today)) > 0;
        int streak = calcStreak(userId, today);
        long totalDays = checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId));
        CheckinLog last = checkinLogMapper.selectOne(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId).orderByDesc(CheckinLog::getCheckinDate).last("LIMIT 1"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkedToday", checkedToday);
        data.put("streak", streak);
        data.put("totalDays", totalDays);
        data.put("todayReward", checkedToday ? 0 : calcReward(streak));
        data.put("lastCheckinAt", last != null ? last.getCreatedAt() : null);
        data.put("calendar", buildCalendar(userId, today));
        data.put("tips", List.of("连续签到可叠加积分", "满 7 天额外 +30 积分", "漏签会重置连续天数"));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> checkin(long userId) {
        userService.requireActiveUser(userId);
        LocalDate today = LocalDate.now();
        if (checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId).eq(CheckinLog::getCheckinDate, today)) > 0) {
            throw BizException.unprocessable("今日已签到");
        }
        int streak = calcStreak(userId, today.minusDays(1)) + 1;
        int reward = calcReward(streak);
        if (streak >= 7 && streak % 7 == 0) {
            reward += 30;
        }
        CheckinLog log = new CheckinLog();
        log.setUserId(userId);
        log.setCheckinDate(today);
        log.setReward(reward);
        checkinLogMapper.insert(log);
        pointsService.addIncome(userId, reward, "每日签到", "checkin", log.getId());
        pointsService.addExp(userId, 5);

        Map<String, Object> data = status(userId);
        data.put("todayReward", reward);
        data.put("points", pointsService.availableBalance(userId));
        return data;
    }

    private int calcReward(int streak) {
        return 10 + Math.min(streak, 7) * 2;
    }

    private int calcStreak(long userId, LocalDate from) {
        int streak = 0;
        LocalDate d = from;
        while (checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId).eq(CheckinLog::getCheckinDate, d)) > 0) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    private List<Map<String, Object>> buildCalendar(long userId, LocalDate today) {
        List<Map<String, Object>> calendar = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            boolean checked = checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                    .eq(CheckinLog::getUserId, userId).eq(CheckinLog::getCheckinDate, d)) > 0;
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("label", d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA));
            day.put("day", d.getDayOfMonth());
            day.put("checked", checked);
            day.put("isToday", d.equals(today));
            calendar.add(day);
        }
        return calendar;
    }
}
