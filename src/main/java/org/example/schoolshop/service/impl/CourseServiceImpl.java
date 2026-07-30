package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.Course;
import org.example.schoolshop.domain.CourseReview;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.CourseReviewRequest;
import org.example.schoolshop.mapper.CourseMapper;
import org.example.schoolshop.mapper.CourseReviewMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.CourseService;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final ContentSecurityService contentSecurityService;
    private final PointsService pointsService;

    @Override
    public Map<String, Object> list(String keyword, String sortBy) {
        LambdaQueryWrapper<Course> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Course::getName, keyword).or().like(Course::getTeacher, keyword));
        }
        if ("rating".equals(sortBy)) {
            qw.orderByDesc(Course::getRating);
        } else {
            qw.orderByDesc(Course::getReviewCount);
        }
        List<Map<String, Object>> list = courseMapper.selectList(qw).stream().map(this::toCourseMap)
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return data;
    }

    @Override
    public Map<String, Object> detail(long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw BizException.notFound("课程不存在");
        }
        Map<String, Object> data = toCourseMap(course);
        List<CourseReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<CourseReview>()
                .eq(CourseReview::getCourseId, courseId).orderByDesc(CourseReview::getCreatedAt).last("LIMIT 20"));
        data.put("reviews", reviews.stream().map(r -> {
            User u = userMapper.selectById(r.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("courseId", r.getCourseId());
            m.put("user", VoAssembler.toUserBrief(u));
            m.put("rating", r.getRating());
            m.put("content", r.getContent());
            m.put("likes", r.getLikes());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(Collectors.toList()));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> addReview(long userId, long courseId, CourseReviewRequest request) {
        userService.requireActiveUser(userId);
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw BizException.notFound("课程不存在");
        }
        contentSecurityService.checkText(request.getContent());
        CourseReview review = new CourseReview();
        review.setCourseId(courseId);
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setLikes(0);
        reviewMapper.insert(review);

        List<CourseReview> all = reviewMapper.selectList(new LambdaQueryWrapper<CourseReview>()
                .eq(CourseReview::getCourseId, courseId));
        double avg = all.stream().mapToInt(CourseReview::getRating).average().orElse(request.getRating());
        course.setRating(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        course.setReviewCount(all.size());
        courseMapper.updateById(course);
        pointsService.addIncome(userId, 5, "课程评价奖励", "course", courseId);

        User u = userMapper.selectById(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", review.getId());
        data.put("courseId", courseId);
        data.put("user", VoAssembler.toUserBrief(u));
        data.put("rating", review.getRating());
        data.put("content", review.getContent());
        data.put("likes", 0);
        data.put("createdAt", review.getCreatedAt());
        return data;
    }

    private Map<String, Object> toCourseMap(Course c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("teacher", c.getTeacher());
        m.put("college", c.getCollege());
        m.put("rating", c.getRating());
        m.put("reviewCount", c.getReviewCount());
        m.put("difficulty", c.getDifficulty());
        m.put("useful", c.getUseful());
        m.put("tags", c.getTags());
        m.put("cover", c.getCover());
        return m;
    }
}
