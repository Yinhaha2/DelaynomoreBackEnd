package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.dto.req.RedeemRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.WalletService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final PointsService pointsService;

    @Override
    public PointsAccountVO getWallet(long userId) {
        return pointsService.getAccount(userId);
    }

    @Override
    public Map<String, Object> withdraw(long userId, org.example.schoolshop.dto.req.WithdrawRequest request) {
        RedeemRequest redeem = new RedeemRequest();
        redeem.setAmount(request.getAmount());
        redeem.setRemark("积分兑换好物");
        return pointsService.redeem(userId, redeem);
    }
}
