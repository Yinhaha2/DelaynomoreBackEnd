package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.CreatePartnerRequest;
import org.example.schoolshop.service.PartnerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(partnerService.list(category, keyword));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreatePartnerRequest request) {
        return Result.ok(partnerService.create(UserContext.requireUserId(), request));
    }

    @PostMapping("/{id}/join")
    public Result<Map<String, Object>> join(@PathVariable long id) {
        return Result.ok(partnerService.join(UserContext.requireUserId(), id));
    }
}
