package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.dto.req.AuditRequest;
import org.example.schoolshop.dto.req.PointsAdjustRequest;
import org.example.schoolshop.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/posts")
    public Result<Map<String, Object>> posts(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(adminService.listPosts(status, page, pageSize));
    }

    @PostMapping("/posts/{id}/audit")
    public Result<Void> auditPost(@PathVariable long id, @RequestBody AuditRequest request) {
        adminService.auditPost(id, request);
        return Result.ok(null);
    }

    @GetMapping("/materials")
    public Result<Map<String, Object>> materials(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(adminService.listMaterials(status, page, pageSize));
    }

    @PostMapping("/materials/{id}/audit")
    public Result<Void> auditMaterial(@PathVariable long id, @RequestBody AuditRequest request) {
        adminService.auditMaterial(id, request);
        return Result.ok(null);
    }

    @GetMapping("/reports")
    public Result<Map<String, Object>> reports(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(adminService.listReports(page, pageSize));
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handleReport(@PathVariable long id, @RequestBody AuditRequest request) {
        adminService.handleReport(id, request);
        return Result.ok(null);
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> users(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(adminService.listUsers(page, pageSize));
    }

    @PostMapping("/users/{id}/ban")
    public Result<Void> banUser(@PathVariable long id, @RequestParam(defaultValue = "true") boolean banned) {
        adminService.banUser(id, banned);
        return Result.ok(null);
    }

    @PostMapping("/points/{userId}/adjust")
    public Result<Map<String, Object>> adjustPoints(@PathVariable long userId,
                                                     @Valid @RequestBody PointsAdjustRequest request) {
        return Result.ok(adminService.adjustPoints(userId, request));
    }
}
