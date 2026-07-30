package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.WithdrawRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;

import java.util.Map;

public interface WalletService {

    PointsAccountVO getWallet(long userId);

    Map<String, Object> withdraw(long userId, WithdrawRequest request);
}
