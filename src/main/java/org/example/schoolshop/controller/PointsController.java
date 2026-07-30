package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.ExchangeRequest;
import org.example.schoolshop.dto.req.RedeemRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;
import org.example.schoolshop.service.PointsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping("/account")
    public Result<PointsAccountVO> account() {
        return Result.ok(pointsService.getAccount(UserContext.requireUserId()));
    }

    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@Valid @RequestBody RedeemRequest request) {
        return Result.ok(pointsService.redeem(UserContext.requireUserId(), request));
    }

    @PostMapping("/exchange")
    public Result<Map<String, Object>> exchange(@Valid @RequestBody ExchangeRequest request) {
        return Result.ok(pointsService.exchange(UserContext.requireUserId(), request));
    }
}
