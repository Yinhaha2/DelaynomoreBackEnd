package org.example.schoolshop.controller;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.service.CheckinService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @GetMapping("/api/checkin/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(checkinService.status(UserContext.requireUserId()));
    }

    @PostMapping("/api/checkin")
    public Result<Map<String, Object>> checkin() {
        return Result.ok(checkinService.checkin(UserContext.requireUserId()));
    }
}
