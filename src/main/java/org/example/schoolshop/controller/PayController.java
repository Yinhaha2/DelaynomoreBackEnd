package org.example.schoolshop.controller;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.service.PayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /** v1.1 已废弃微信支付，保留兼容空实现 */
    @PostMapping("/notify/wechat")
    public Map<String, Object> wechatNotify(@RequestBody(required = false) String body) {
        payService.wechatNotify(body != null ? body : "");
        return Map.of("code", "SUCCESS", "message", "成功", "mock", true);
    }
}
