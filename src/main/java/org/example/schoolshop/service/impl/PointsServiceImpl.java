package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.LevelUtil;
import org.example.schoolshop.domain.TradeOrder;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.domain.WalletRecord;
import org.example.schoolshop.dto.req.ExchangeRequest;
import org.example.schoolshop.dto.req.RedeemRequest;
import org.example.schoolshop.dto.vo.PointsAccountVO;
import org.example.schoolshop.mapper.TradeOrderMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.mapper.WalletRecordMapper;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final int PLATFORM_FEE_PERCENT = 5;

    private final UserMapper userMapper;
    private final WalletRecordMapper walletRecordMapper;
    private final TradeOrderMapper orderMapper;
    private final UserService userService;

    @Override
    public PointsAccountVO getAccount(long userId) {
        User user = userService.requireActiveUser(userId);
        int frozen = user.getWalletFrozen() != null ? user.getWalletFrozen() : 0;
        int balance = user.getWalletBalance() - frozen;
        int exp = user.getExp() != null ? user.getExp() : 0;
        int level = user.getLevel() != null ? user.getLevel() : LevelUtil.levelFromExp(exp);

        List<WalletRecord> records = walletRecordMapper.selectList(new LambdaQueryWrapper<WalletRecord>()
                .eq(WalletRecord::getUserId, userId)
                .orderByDesc(WalletRecord::getCreatedAt)
                .last("LIMIT 20"));

        PointsAccountVO vo = new PointsAccountVO();
        vo.setBalance(balance);
        vo.setLevel(level);
        vo.setLevelName(LevelUtil.levelName(level));
        vo.setExp(exp);
        vo.setNextLevelExp(LevelUtil.nextLevelExp(level));
        vo.setRecords(records.stream().map(r -> {
            PointsAccountVO.PointsRecordVO rv = new PointsAccountVO.PointsRecordVO();
            rv.setId(r.getId());
            rv.setType(r.getType());
            rv.setAmount(r.getAmount());
            rv.setRemark(r.getRemark());
            rv.setCreatedAt(r.getCreatedAt());
            return rv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Object> redeem(long userId, RedeemRequest request) {
        userService.requireActiveUser(userId);
        if (request.getAmount() == null || request.getAmount() < 1) {
            throw BizException.badRequest("参数错误");
        }
        doDeduct(userId, request.getAmount(),
                request.getRemark() != null ? request.getRemark() : "积分兑换好物", null, null);

        TradeOrder order = new TradeOrder();
        order.setOrderNo("R" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setType("redeem");
        order.setBizId(0L);
        order.setTitle(request.getRemark() != null ? request.getRemark() : "积分兑换好物");
        order.setAmount(request.getAmount());
        order.setStatus(1);
        order.setOutTradeNo("REDEEM_" + UUID.randomUUID().toString().substring(0, 8));
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("id", order.getId());
        data.put("status", "completed");
        data.put("balance", availableBalance(userId));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> exchange(long userId, ExchangeRequest request) {
        userService.requireActiveUser(userId);
        if (request.getCost() == null || request.getCost() < 1) {
            throw BizException.badRequest("参数错误");
        }
        doDeduct(userId, request.getCost(),
                request.getRemark() != null ? request.getRemark() : "积分消费", null, null);

        TradeOrder order = new TradeOrder();
        order.setOrderNo("E" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setType("redeem");
        order.setBizId(0L);
        order.setTitle(request.getRemark() != null ? request.getRemark() : "积分消费");
        order.setAmount(request.getCost());
        order.setStatus(1);
        order.setOutTradeNo("EX_" + UUID.randomUUID().toString().substring(0, 8));
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("balance", availableBalance(userId));
        data.put("orderId", order.getId());
        return data;
    }

    @Override
    @Transactional
    public void freeze(long userId, int amount, String remark, String bizType, Long bizId) {
        if (amount < 1) {
            throw BizException.badRequest("积分金额无效");
        }
        int rows = userMapper.freezePoints(userId, amount);
        if (rows == 0) {
            throw BizException.unprocessable("积分不足，快去签到或接单吧");
        }
        insertRecord(userId, "expense", amount, remark, bizType, bizId);
    }

    @Override
    @Transactional
    public void unfreeze(long userId, int amount, String remark) {
        int rows = userMapper.unfreezePoints(userId, amount);
        if (rows == 0) {
            throw BizException.badRequest("解冻失败");
        }
        insertRecord(userId, "income", amount, remark, "task", null);
    }

    @Override
    @Transactional
    public void settleTaskReward(long publisherId, long acceptorId, int rewardAmount, long taskId) {
        int fee = rewardAmount * PLATFORM_FEE_PERCENT / 100;
        int net = rewardAmount - fee;
        userMapper.addPoints(acceptorId, net);
        insertRecord(acceptorId, "income", net, "代办悬赏收入", "task", taskId);
        addExp(acceptorId, 10);
    }

    @Override
    public int availableBalance(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return 0;
        }
        int frozen = user.getWalletFrozen() != null ? user.getWalletFrozen() : 0;
        return user.getWalletBalance() - frozen;
    }

    @Override
    @Transactional
    public void addIncome(long userId, int amount, String remark, String bizType, Long bizId) {
        userMapper.addPoints(userId, amount);
        insertRecord(userId, "income", amount, remark, bizType, bizId);
    }

    @Override
    @Transactional
    public void addExp(long userId, int exp) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int newExp = (user.getExp() != null ? user.getExp() : 0) + exp;
        userMapper.addExp(userId, exp, LevelUtil.levelFromExp(newExp));
    }

    @Override
    @Transactional
    public void deduct(long userId, int amount, String remark, String bizType, Long bizId) {
        doDeduct(userId, amount, remark, bizType, bizId);
    }

    private void doDeduct(long userId, int amount, String remark, String bizType, Long bizId) {
        int rows = userMapper.deductPoints(userId, amount);
        if (rows == 0) {
            throw BizException.unprocessable("积分不足，快去签到或接单吧");
        }
        insertRecord(userId, "expense", amount, remark, bizType, bizId);
    }

    private void insertRecord(long userId, String type, int amount, String remark,
                              String bizType, Long bizId) {
        WalletRecord record = new WalletRecord();
        record.setUserId(userId);
        record.setType(type);
        record.setAmount(amount);
        record.setRemark(remark);
        record.setBizType(bizType);
        record.setBizId(bizId);
        walletRecordMapper.insert(record);
    }
}
