package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.UpdateProfileRequest;
import org.example.schoolshop.dto.vo.UserHomeVO;
import org.example.schoolshop.dto.vo.UserVO;
import org.example.schoolshop.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/user/profile")
    public Result<UserVO> profile() {
        return Result.ok(userService.getProfile(UserContext.requireUserId()));
    }

    @PutMapping("/api/user/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(UserContext.requireUserId(), request));
    }

    @GetMapping("/api/users/{userId}/home")
    public Result<UserHomeVO> home(@PathVariable Long userId) {
        Long current = UserContext.getUserId();
        return Result.ok(userService.getUserHome(userId, current));
    }

    @PostMapping("/api/users/{userId}/follow")
    public Result<Map<String, Boolean>> follow(@PathVariable Long userId) {
        return Result.ok(userService.toggleFollow(UserContext.requireUserId(), userId));
    }
}
