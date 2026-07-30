package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.ReportRequest;
import org.example.schoolshop.service.ReportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody ReportRequest request) {
        return Result.ok(reportService.create(UserContext.requireUserId(), request));
    }
}
