package org.example.schoolshop.service;

import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.RealNameVerifyRequest;
import org.example.schoolshop.dto.req.UpdateProfileRequest;
import org.example.schoolshop.dto.vo.UserHomeVO;
import org.example.schoolshop.dto.vo.UserVO;

import java.util.Map;

public interface UserService {

    User requireActiveUser(Long userId);

    UserVO getProfile(long userId);

    UserVO updateProfile(long userId, UpdateProfileRequest request);

    UserHomeVO getUserHome(long targetUserId, Long currentUserId);

    Map<String, Boolean> toggleFollow(long followerId, long followeeId);

    UserVO realNameVerify(long userId, RealNameVerifyRequest request);
}
