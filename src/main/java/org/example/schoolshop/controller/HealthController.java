package org.example.schoolshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.config.SchoolShopProperties;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final UserMapper userMapper;
    private final SchoolShopProperties properties;

    /**
     * 连通性检查：数据源 + 查询 init.sql 初始用户
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            data.put("database", conn.getCatalog());
            data.put("dbConnected", true);
        } catch (Exception e) {
            return Result.fail(500, "数据库连接失败: " + e.getMessage());
        }
        // health 不经过业务拦截器的登录要求已在白名单

        long userCount = userMapper.selectCount(null);
        User demo = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, "mock_openid_001").last("LIMIT 1"));
        data.put("userCount", userCount);
        if (demo != null) {
            Map<String, Object> demoUser = new LinkedHashMap<>();
            demoUser.put("id", demo.getId());
            demoUser.put("nickname", demo.getNickname());
            demoUser.put("walletBalance", demo.getWalletBalance());
            data.put("demoUser", demoUser);
        }
        data.put("status", "UP");
        Map<String, Boolean> integrations = new LinkedHashMap<>();
        integrations.put("wechat", properties.isWechatConfigured());
        integrations.put("oss", properties.isOssConfigured());
        integrations.put("ai", properties.isAiConfigured());
        integrations.put("admin", properties.isAdminConfigured());
        data.put("integrations", integrations);
        return Result.ok(data);
    }
}
