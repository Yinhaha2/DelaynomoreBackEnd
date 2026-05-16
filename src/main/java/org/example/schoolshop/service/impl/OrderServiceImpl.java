package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.domain.TradeOrder;
import org.example.schoolshop.dto.vo.OrderItemVO;
import org.example.schoolshop.mapper.TradeOrderMapper;
import org.example.schoolshop.service.OrderService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final TradeOrderMapper orderMapper;
    private final UserService userService;

    @Override
    public PageResult<OrderItemVO> myOrders(long userId, String type, Integer page, Integer pageSize) {
        userService.requireActiveUser(userId);
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<TradeOrder> qw = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId).orderByDesc(TradeOrder::getCreatedAt);
        if (StringUtils.hasText(type)) {
            qw.eq(TradeOrder::getType, type);
        }
        Page<TradeOrder> pageData = orderMapper.selectPage(new Page<>(p, ps), qw);
        return PageResult.of(pageData.convert(VoAssembler::toOrderItem));
    }
}
