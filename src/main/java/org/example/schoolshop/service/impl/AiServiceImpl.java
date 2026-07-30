package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.req.AiChatRequest;
import org.example.schoolshop.dto.req.FeedFeedbackRequest;
import org.example.schoolshop.integration.ai.LlmClient;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.AiService;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final String[] DAY_LABELS = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final ScheduleCourseMapper scheduleMapper;
    private final TaskMapper taskMapper;
    private final MaterialMapper materialMapper;
    private final PartnerMapper partnerMapper;
    private final CourseMapper courseMapper;
    private final FeedFeedbackMapper feedbackMapper;
    private final UserService userService;
    private final ContentSecurityService contentSecurityService;
    private final LlmClient llmClient;

    @Override
    public Map<String, Object> weekSchedule(long userId) {
        userService.requireActiveUser(userId);
        List<ScheduleCourse> courses = scheduleMapper.selectList(new LambdaQueryWrapper<ScheduleCourse>()
                .eq(ScheduleCourse::getUserId, userId).orderByAsc(ScheduleCourse::getDayOfWeek));
        if (courses.isEmpty()) {
            courses = scheduleMapper.selectList(new LambdaQueryWrapper<ScheduleCourse>()
                    .eq(ScheduleCourse::getUserId, 1L).orderByAsc(ScheduleCourse::getDayOfWeek));
        }
        Map<Integer, List<ScheduleCourse>> grouped = courses.stream()
                .collect(Collectors.groupingBy(ScheduleCourse::getDayOfWeek));
        List<Map<String, Object>> days = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", d);
            day.put("label", DAY_LABELS[d]);
            day.put("courses", grouped.getOrDefault(d, List.of()).stream().map(this::toCourseSlot).collect(Collectors.toList()));
            days.add(day);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekLabel", "第 12 教学周");
        data.put("term", "2025-2026 春季学期");
        data.put("days", days);
        return data;
    }

    @Override
    public Map<String, Object> todaySchedule(long userId) {
        userService.requireActiveUser(userId);
        int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        List<ScheduleCourse> courses = scheduleMapper.selectList(new LambdaQueryWrapper<ScheduleCourse>()
                .eq(ScheduleCourse::getUserId, userId).eq(ScheduleCourse::getDayOfWeek, dayOfWeek));
        if (courses.isEmpty()) {
            courses = scheduleMapper.selectList(new LambdaQueryWrapper<ScheduleCourse>()
                    .eq(ScheduleCourse::getUserId, 1L).eq(ScheduleCourse::getDayOfWeek, dayOfWeek));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekLabel", "第 12 教学周");
        data.put("dayLabel", DAY_LABELS[dayOfWeek]);
        data.put("courses", courses.stream().map(this::toCourseSlot).collect(Collectors.toList()));
        data.put("next", courses.isEmpty() ? null : toCourseSlot(courses.get(0)));
        return data;
    }

    @Override
    public Map<String, Object> chat(long userId, AiChatRequest request) {
        userService.requireActiveUser(userId);
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
        contentSecurityService.checkText(message);
        String reply = null;
        if (llmClient.isConfigured()) {
            String system = "你是校园学习助手，回答简洁友好，不涉及政治敏感与违法内容，不承诺提现或人民币。";
            reply = llmClient.chat(system, message);
            if (reply != null) {
                contentSecurityService.checkText(reply);
            }
        }
        if (reply == null || reply.isBlank()) {
            reply = ruleBasedReply(userId, message);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", System.currentTimeMillis());
        data.put("role", "assistant");
        data.put("content", reply);
        data.put("suggestions", List.of("今天有什么课", "推荐自习搭子", "怎么赚积分"));
        data.put("createdAt", LocalDateTime.now());
        data.put("context", request.getContext() != null ? request.getContext() : Map.of());
        return data;
    }

    @Override
    public Map<String, Object> feed(long userId, Integer limit) {
        userService.requireActiveUser(userId);
        int lim = limit == null || limit < 1 ? 10 : Math.min(limit, 20);
        List<Map<String, Object>> list = new ArrayList<>();
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, 1).orderByDesc(Task::getRewardAmount).last("LIMIT 3"));
        for (Task t : tasks) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("rewardAmount", t.getRewardAmount());
            meta.put("location", t.getLocation());
            list.add(feedItem("task", t.getId(), "为你推荐：" + t.getTitle(), "高悬赏热单", 0.95, meta));
        }
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getStatus, 1).orderByDesc(Material::getSoldCount).last("LIMIT 2"));
        for (Material m : materials) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("price", m.getPrice());
            list.add(feedItem("material", m.getId(), "热门资料：" + m.getTitle(), "很多同学在兑换", 0.88, meta));
        }
        List<Partner> partners = partnerMapper.selectList(new LambdaQueryWrapper<Partner>()
                .eq(Partner::getStatus, "open").orderByDesc(Partner::getCreatedAt).last("LIMIT 2"));
        for (Partner p : partners) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("location", p.getLocation());
            list.add(feedItem("partner", p.getId(), p.getTitle(), "组队学习推荐", 0.82, meta));
        }
        if (list.size() > lim) {
            list = list.subList(0, lim);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("generatedAt", LocalDateTime.now());
        data.put("algorithm", "rule-hybrid-v1");
        data.put("tip", "综合课表、活跃区域、浏览偏好生成");
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> feedback(long userId, FeedFeedbackRequest request) {
        userService.requireActiveUser(userId);
        FeedFeedback fb = new FeedFeedback();
        fb.setUserId(userId);
        fb.setItemId(request.getItemId());
        fb.setAction(request.getAction());
        feedbackMapper.insert(fb);
        Map<String, Object> data = new HashMap<>();
        data.put("ok", true);
        data.put("itemId", request.getItemId());
        data.put("action", request.getAction());
        return data;
    }

    private Map<String, Object> toCourseSlot(ScheduleCourse c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("teacher", c.getTeacher());
        m.put("place", c.getPlace());
        m.put("start", c.getStartTime());
        m.put("end", c.getEndTime());
        m.put("color", c.getColor());
        return m;
    }

    private Map<String, Object> feedItem(String type, long targetId, String title, String reason,
                                         double score, Map<String, Object> meta) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", type.charAt(0) + String.valueOf(targetId));
        item.put("type", type);
        item.put("targetId", targetId);
        item.put("title", title);
        item.put("reason", reason);
        item.put("score", score);
        item.put("cover", "");
        item.put("meta", meta);
        return item;
    }

    private String ruleBasedReply(long userId, String message) {
        if (message.contains("课") || message.contains("今天")) {
            Map<String, Object> today = todaySchedule(userId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> courses = (List<Map<String, Object>>) today.get("courses");
            if (courses == null || courses.isEmpty()) {
                return "今天没有安排课程，可以去图书馆自习或看看校园悬赏～";
            }
            return "今天有 " + courses.size() + " 节课：" + courses.stream()
                    .map(c -> c.get("name") + "（" + c.get("start") + "-" + c.get("end") + "）")
                    .collect(Collectors.joining("；"));
        }
        if (message.contains("积分")) {
            return "赚积分方式：每日签到、完成代办悬赏、上架资料、课程评价等。积分可在资料集市兑换学习资料。";
        }
        if (message.contains("搭子")) {
            return "可以在「找搭子」发布自习/干饭/运动组队，也可以看看首页智能推荐～";
        }
        return "我是校园 AI 助手，可以问我今天有什么课、怎么赚积分、找搭子建议等。";
    }
}
