package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.CourseReviewRequest;
import org.example.schoolshop.service.CourseService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "hot") String sortBy) {
        return Result.ok(courseService.list(keyword, sortBy));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.ok(courseService.detail(id));
    }

    @PostMapping("/{id}/reviews")
    public Result<Map<String, Object>> review(@PathVariable long id,
                                                @Valid @RequestBody CourseReviewRequest request) {
        return Result.ok(courseService.addReview(UserContext.requireUserId(), id, request));
    }
}
