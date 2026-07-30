package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.config.SchoolShopProperties;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.WxLoginRequest;
import org.example.schoolshop.dto.vo.LoginVO;
import org.example.schoolshop.integration.wechat.WeChatClient;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.AuthService;
import org.example.schoolshop.util.JwtUtil;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WeChatClient weChatClient;
    private final SchoolShopProperties properties;

    @Override
    @Transactional
    public LoginVO wxLogin(WxLoginRequest request) {
        String openid;
        String unionid = null;
        if (weChatClient.isConfigured()) {
            WeChatClient.WxSession session = weChatClient.code2Session(request.getCode());
            openid = session.getOpenid();
            unionid = session.getUnionid();
        } else if (properties.getWechat().isMockEnabled()) {
            openid = "mock_" + request.getCode();
            log.debug("微信未配置，使用 mock 登录 openid={}", openid);
        } else {
            throw BizException.unauthorized("微信登录未配置，请联系管理员");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(unionid);
            user.setNickname("微信用户");
            user.setAvatar("");
            user.setRealNameVerified(false);
            user.setStatus(0);
            user.setWalletBalance(1000);
            user.setWalletFrozen(0);
            user.setLevel(1);
            user.setExp(0);
            userMapper.insert(user);
        } else if (unionid != null && user.getUnionid() == null) {
            user.setUnionid(unionid);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generate(user.getId()));
        vo.setUser(VoAssembler.toUserVO(user));
        return vo;
    }
}
