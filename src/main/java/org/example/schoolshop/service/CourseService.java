package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.CourseReviewRequest;

import java.util.Map;

public interface CourseService {
    Map<String, Object> list(String keyword, String sortBy);
    Map<String, Object> detail(long courseId);
    Map<String, Object> addReview(long userId, long courseId, CourseReviewRequest request);
}
