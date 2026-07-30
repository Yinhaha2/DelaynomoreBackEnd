package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.AiChatRequest;
import org.example.schoolshop.dto.req.FeedFeedbackRequest;
import org.example.schoolshop.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @GetMapping("/schedule")
    public Result<Map<String, Object>> schedule() {
        return Result.ok(aiService.weekSchedule(UserContext.requireUserId()));
    }

    @GetMapping("/schedule/today")
    public Result<Map<String, Object>> today() {
        return Result.ok(aiService.todaySchedule(UserContext.requireUserId()));
    }

    @PostMapping("/assistant/chat")
    public Result<Map<String, Object>> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.ok(aiService.chat(UserContext.requireUserId(), request));
    }

    @GetMapping("/feed")
    public Result<Map<String, Object>> feed(@RequestParam(required = false) Integer limit) {
        return Result.ok(aiService.feed(UserContext.requireUserId(), limit));
    }

    @PostMapping("/feed/feedback")
    public Result<Map<String, Object>> feedback(@Valid @RequestBody FeedFeedbackRequest request) {
        return Result.ok(aiService.feedback(UserContext.requireUserId(), request));
    }
}
