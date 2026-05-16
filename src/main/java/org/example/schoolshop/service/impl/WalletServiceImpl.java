package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.OrderStatusUtil;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.domain.WalletRecord;
import org.example.schoolshop.domain.Withdraw;
import org.example.schoolshop.dto.req.WithdrawRequest;
import org.example.schoolshop.dto.vo.WalletVO;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.mapper.WalletRecordMapper;
import org.example.schoolshop.mapper.WithdrawMapper;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserMapper userMapper;
    private final WalletRecordMapper walletRecordMapper;
    private final WithdrawMapper withdrawMapper;
    private final UserService userService;

    @Override
    public WalletVO getWallet(long userId) {
        User user = userService.requireActiveUser(userId);
        int available = user.getWalletBalance() - (user.getWalletFrozen() != null ? user.getWalletFrozen() : 0);
        List<WalletRecord> records = walletRecordMapper.selectList(new LambdaQueryWrapper<WalletRecord>()
                .eq(WalletRecord::getUserId, userId)
                .orderByDesc(WalletRecord::getCreatedAt).last("LIMIT 20"));
        WalletVO vo = new WalletVO();
        vo.setBalance(available);
        vo.setRecords(records.stream().map(r -> {
            WalletVO.WalletRecordVO rv = new WalletVO.WalletRecordVO();
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
    public Map<String, Object> withdraw(long userId, WithdrawRequest request) {
        User user = userService.requireActiveUser(userId);
        int frozen = user.getWalletFrozen() != null ? user.getWalletFrozen() : 0;
        int available = user.getWalletBalance() - frozen;
        if (request.getAmount() < 100 || request.getAmount() > available) {
            throw BizException.unprocessable("余额不足");
        }
        user.setWalletFrozen(frozen + request.getAmount());
        userMapper.updateById(user);
        Withdraw w = new Withdraw();
        w.setUserId(userId);
        w.setAmount(request.getAmount());
        w.setStatus(0);
        withdrawMapper.insert(w);
        Map<String, Object> data = new HashMap<>();
        data.put("id", w.getId());
        data.put("status", OrderStatusUtil.withdrawToFrontend(0));
        return data;
    }
}
