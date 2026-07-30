package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.ExchangeRequest;
import org.example.schoolshop.dto.req.RedeemRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;

import java.util.Map;

public interface PointsService {

    PointsAccountVO getAccount(long userId);

    Map<String, Object> redeem(long userId, RedeemRequest request);

    Map<String, Object> exchange(long userId, ExchangeRequest request);

    void freeze(long userId, int amount, String remark, String bizType, Long bizId);

    void unfreeze(long userId, int amount, String remark);

    void settleTaskReward(long publisherId, long acceptorId, int rewardAmount, long taskId);

    int availableBalance(long userId);

    void addIncome(long userId, int amount, String remark, String bizType, Long bizId);

    void addExp(long userId, int exp);

    void deduct(long userId, int amount, String remark, String bizType, Long bizId);
}
