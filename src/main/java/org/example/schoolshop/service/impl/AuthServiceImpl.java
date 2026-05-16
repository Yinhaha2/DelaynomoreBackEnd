package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.WxLoginRequest;
import org.example.schoolshop.dto.vo.LoginVO;
import org.example.schoolshop.dto.vo.UserVO;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.AuthService;
import org.example.schoolshop.util.JwtUtil;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public LoginVO wxLogin(WxLoginRequest request) {
        // TODO: 调用微信 jscode2session 换取 openid
        String openid = "wx_" + request.getCode();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("微信用户");
            user.setAvatar("");
            user.setRealNameVerified(false);
            user.setStatus(0);
            user.setWalletBalance(0);
            user.setWalletFrozen(0);
            userMapper.insert(user);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generate(user.getId()));
        vo.setUser(VoAssembler.toUserVO(user));
        return vo;
    }
}
