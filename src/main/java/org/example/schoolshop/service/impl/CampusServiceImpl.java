package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.CampusService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampusServiceImpl implements CampusService {

    private final CampusSpotMapper spotMapper;
    private final TaskMapper taskMapper;
    private final CampusEventMapper eventMapper;
    private final EventJoinMapper eventJoinMapper;
    private final UserService userService;

    @Override
    public Map<String, Object> listSpots(String category, String keyword) {
        LambdaQueryWrapper<CampusSpot> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category) && !"all".equals(category)) {
            qw.eq(CampusSpot::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(CampusSpot::getName, keyword).or().like(CampusSpot::getAlias, keyword));
        }
        List<Map<String, Object>> list = spotMapper.selectList(qw).stream().map(this::toSpotMap).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schoolName", "SchoolShop 大学");
        Map<String, Integer> bounds = new LinkedHashMap<>();
        bounds.put("width", 100);
        bounds.put("height", 100);
        data.put("bounds", bounds);
        data.put("list", list);
        return data;
    }

    @Override
    public Map<String, Object> spotDetail(long id) {
        CampusSpot spot = spotMapper.selectById(id);
        if (spot == null) {
            throw BizException.notFound("点位不存在");
        }
        return toSpotMap(spot);
    }

    @Override
    public Map<String, Object> mapTasks() {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, 1)
                .isNotNull(Task::getFromSpotId)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 20"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Task task : tasks) {
            CampusSpot from = task.getFromSpotId() != null ? spotMapper.selectById(task.getFromSpotId()) : null;
            CampusSpot to = task.getToSpotId() != null ? spotMapper.selectById(task.getToSpotId()) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.getId());
            item.put("title", task.getTitle());
            item.put("rewardAmount", task.getRewardAmount());
            item.put("fromSpotId", task.getFromSpotId());
            item.put("toSpotId", task.getToSpotId());
            item.put("status", task.getStatus());
            if (from != null) {
                item.put("from", miniSpot(from));
            }
            if (to != null) {
                item.put("to", miniSpot(to));
            }
            list.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return data;
    }

    @Override
    public Map<String, Object> listEvents(String month, Long userId) {
        LambdaQueryWrapper<CampusEvent> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(month)) {
            YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
            qw.between(CampusEvent::getStartAt, start, end);
        }
        qw.orderByAsc(CampusEvent::getStartAt);
        List<CampusEvent> events = eventMapper.selectList(qw);
        List<Map<String, Object>> list = events.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("cover", e.getCover());
            m.put("location", e.getLocation());
            m.put("startAt", e.getStartAt());
            m.put("endAt", e.getEndAt());
            m.put("category", e.getCategory());
            m.put("capacity", e.getCapacity());
            m.put("joinedCount", e.getJoinedCount());
            m.put("tags", e.getTags());
            m.put("description", e.getDescription());
            boolean joined = false;
            if (userId != null) {
                joined = eventJoinMapper.selectCount(new LambdaQueryWrapper<EventJoin>()
                        .eq(EventJoin::getEventId, e.getId())
                        .eq(EventJoin::getUserId, userId)) > 0;
            }
            m.put("joined", joined);
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> joinEvent(long userId, long eventId) {
        userService.requireActiveUser(userId);
        CampusEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw BizException.notFound("活动不存在");
        }
        if (eventJoinMapper.selectCount(new LambdaQueryWrapper<EventJoin>()
                .eq(EventJoin::getEventId, eventId).eq(EventJoin::getUserId, userId)) > 0) {
            throw BizException.unprocessable("已报名该活动");
        }
        if (event.getJoinedCount() >= event.getCapacity()) {
            throw BizException.unprocessable("活动已满员");
        }
        EventJoin join = new EventJoin();
        join.setEventId(eventId);
        join.setUserId(userId);
        eventJoinMapper.insert(join);
        event.setJoinedCount(event.getJoinedCount() + 1);
        eventMapper.updateById(event);
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("joined", true);
        data.put("message", "报名成功，已加入日程");
        return data;
    }

    private Map<String, Object> toSpotMap(CampusSpot spot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", spot.getId());
        m.put("name", spot.getName());
        m.put("alias", spot.getAlias());
        m.put("zone", spot.getZone());
        m.put("x", spot.getX());
        m.put("y", spot.getY());
        m.put("category", spot.getCategory());
        m.put("hot", spot.getHot());
        m.put("taskCount", spot.getTaskCount());
        m.put("desc", spot.getDesc());
        return m;
    }

    private Map<String, Object> miniSpot(CampusSpot spot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", spot.getId());
        m.put("name", spot.getName());
        m.put("x", spot.getX());
        m.put("y", spot.getY());
        return m;
    }
}
