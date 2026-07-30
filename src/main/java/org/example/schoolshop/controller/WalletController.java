package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.WithdrawRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;
import org.example.schoolshop.service.WalletService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public Result<PointsAccountVO> wallet() {
        return Result.ok(walletService.getWallet(UserContext.requireUserId()));
    }

    @PostMapping("/withdraw")
    public Result<Map<String, Object>> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return Result.ok(walletService.withdraw(UserContext.requireUserId(), request));
    }
}
