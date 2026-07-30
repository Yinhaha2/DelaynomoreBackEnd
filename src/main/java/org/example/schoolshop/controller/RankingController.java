package org.example.schoolshop.controller;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "helper") String type) {
        return Result.ok(rankingService.list(type));
    }
}
