package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.service.PayService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    @Override
    public String wechatNotify(String body) {
        // TODO: 验签、幂等、更新 order/task/material
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }
}
