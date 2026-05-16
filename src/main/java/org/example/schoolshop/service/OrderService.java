package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.vo.OrderItemVO;

public interface OrderService {

    PageResult<OrderItemVO> myOrders(long userId, String type, Integer page, Integer pageSize);
}
