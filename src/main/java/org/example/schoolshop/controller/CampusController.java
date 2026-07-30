package org.example.schoolshop.controller;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.service.CampusService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusController {

    private final CampusService campusService;

    @GetMapping("/spots")
    public Result<Map<String, Object>> spots(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(campusService.listSpots(category, keyword));
    }

    @GetMapping("/spots/{id}")
    public Result<Map<String, Object>> spotDetail(@PathVariable long id) {
        return Result.ok(campusService.spotDetail(id));
    }

    @GetMapping("/map/tasks")
    public Result<Map<String, Object>> mapTasks() {
        return Result.ok(campusService.mapTasks());
    }

    @GetMapping("/events")
    public Result<Map<String, Object>> events(@RequestParam(required = false) String month) {
        return Result.ok(campusService.listEvents(month, UserContext.getUserId()));
    }

    @PostMapping("/events/{id}/join")
    public Result<Map<String, Object>> joinEvent(@PathVariable long id) {
        return Result.ok(campusService.joinEvent(UserContext.requireUserId(), id));
    }
}
