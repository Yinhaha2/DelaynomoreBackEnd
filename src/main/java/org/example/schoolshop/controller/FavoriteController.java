package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.FavoriteToggleRequest;
import org.example.schoolshop.service.FavoriteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "all") String type) {
        return Result.ok(favoriteService.list(UserContext.requireUserId(), type));
    }

    @PostMapping("/toggle")
    public Result<Map<String, Object>> toggle(@Valid @RequestBody FavoriteToggleRequest request) {
        return Result.ok(favoriteService.toggle(UserContext.requireUserId(), request));
    }
}
