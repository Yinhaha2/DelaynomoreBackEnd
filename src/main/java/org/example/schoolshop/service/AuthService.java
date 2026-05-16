package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.WxLoginRequest;
import org.example.schoolshop.dto.vo.LoginVO;

public interface AuthService {
    LoginVO wxLogin(WxLoginRequest request);
}
