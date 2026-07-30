package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.BadgeService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    private final BadgeMapper badgeMapper;
    private final UserBadgeMapper userBadgeMapper;
    private final MaterialMapper materialMapper;
    private final TaskMapper taskMapper;
    private final CheckinLogMapper checkinLogMapper;
    private final UserService userService;

    @Override
    public Map<String, Object> list(long userId) {
        userService.requireActiveUser(userId);
        List<Badge> badges = badgeMapper.selectList(null);
        int unlockedCount = 0;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Badge badge : badges) {
            UserBadge ub = userBadgeMapper.selectOne(new LambdaQueryWrapper<UserBadge>()
                    .eq(UserBadge::getUserId, userId).eq(UserBadge::getBadgeId, badge.getId()));
            int progress = calcProgress(userId, badge);
            boolean unlocked = ub != null || progress >= badge.getTargetValue();
            if (unlocked && ub == null) {
                ub = new UserBadge();
                ub.setUserId(userId);
                ub.setBadgeId(badge.getId());
                userBadgeMapper.insert(ub);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", badge.getId());
            m.put("name", badge.getName());
            m.put("icon", badge.getIcon());
            m.put("desc", badge.getDesc());
            m.put("rarity", badge.getRarity());
            m.put("unlocked", unlocked);
            if (unlocked) {
                unlockedCount++;
                m.put("unlockedAt", ub != null && ub.getUnlockedAt() != null
                        ? ub.getUnlockedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
            } else {
                m.put("progress", progress);
                m.put("target", badge.getTargetValue());
            }
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("unlocked", unlockedCount);
        return data;
    }

    private int calcProgress(long userId, Badge badge) {
        return switch (badge.getTargetType()) {
            case "login" -> 1;
            case "checkin" -> checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                    .eq(CheckinLog::getUserId, userId)).intValue();
            case "material" -> materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                    .eq(Material::getUserId, userId)).intValue();
            case "task" -> taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                    .eq(Task::getAcceptorId, userId).eq(Task::getStatus, 4)).intValue();
            default -> 0;
        };
    }
}
