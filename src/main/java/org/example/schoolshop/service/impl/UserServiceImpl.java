package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.req.RealNameVerifyRequest;
import org.example.schoolshop.dto.req.UpdateProfileRequest;
import org.example.schoolshop.dto.vo.MaterialItemVO;
import org.example.schoolshop.dto.vo.PostItemVO;
import org.example.schoolshop.dto.vo.UserHomeVO;
import org.example.schoolshop.dto.vo.UserVO;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final PostMapper postMapper;
    private final TaskMapper taskMapper;
    private final MaterialMapper materialMapper;
    private final ActivityNotificationMapper notificationMapper;

    @Override
    public User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw BizException.forbidden("账号已被封禁");
        }
        return user;
    }

    @Override
    public UserVO getProfile(long userId) {
        return VoAssembler.toUserVO(requireActiveUser(userId));
    }

    @Override
    public UserVO updateProfile(long userId, UpdateProfileRequest request) {
        User user = requireActiveUser(userId);
        if (request.getNickname() != null) {
            String nick = request.getNickname().trim();
            if (nick.length() < 2 || nick.length() > 20) {
                throw BizException.badRequest("昵称长度 2~20");
            }
            user.setNickname(nick);
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        userMapper.updateById(user);
        return VoAssembler.toUserVO(user);
    }

    @Override
    public UserHomeVO getUserHome(long targetUserId, Long currentUserId) {
        User target = requireActiveUser(targetUserId);
        UserHomeVO home = new UserHomeVO();
        UserHomeVO.UserProfileVO profile = new UserHomeVO.UserProfileVO();
        profile.setId(target.getId());
        profile.setNickname(target.getNickname());
        profile.setAvatar(target.getAvatar());
        profile.setBio(target.getBio());
        profile.setPostCount(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, targetUserId).eq(Post::getStatus, 1).eq(Post::getIsDeleted, 0)));
        profile.setTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getPublisherId, targetUserId)));
        profile.setMaterialCount(materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, targetUserId).eq(Material::getStatus, 1)));
        profile.setFollowed(false);
        if (currentUserId != null && !currentUserId.equals(targetUserId)) {
            long cnt = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUserId)
                    .eq(UserFollow::getFolloweeId, targetUserId));
            profile.setFollowed(cnt > 0);
        }
        home.setUser(profile);

        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, targetUserId).eq(Post::getStatus, 1).eq(Post::getIsDeleted, 0)
                .orderByDesc(Post::getCreatedAt).last("LIMIT 20"));
        home.setPosts(posts.stream().map(p -> VoAssembler.toPostItem(p, target, false)).collect(Collectors.toList()));

        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, targetUserId).eq(Material::getStatus, 1)
                .orderByDesc(Material::getCreatedAt).last("LIMIT 20"));
        home.setMaterials(materials.stream().map(VoAssembler::toMaterialItem).collect(Collectors.toList()));
        return home;
    }

    @Override
    @Transactional
    public Map<String, Boolean> toggleFollow(long followerId, long followeeId) {
        if (followerId == followeeId) {
            throw BizException.unprocessable("不能关注自己");
        }
        requireActiveUser(followeeId);
        UserFollow existing = userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        boolean followed;
        if (existing != null) {
            userFollowMapper.deleteById(existing.getId());
            followed = false;
        } else {
            UserFollow f = new UserFollow();
            f.setFollowerId(followerId);
            f.setFolloweeId(followeeId);
            userFollowMapper.insert(f);
            followed = true;
            ActivityNotification n = new ActivityNotification();
            n.setUserId(followeeId);
            n.setActorId(followerId);
            n.setType("FOLLOW");
            n.setRead(0);
            notificationMapper.insert(n);
        }
        Map<String, Boolean> result = new HashMap<>();
        result.put("followed", followed);
        return result;
    }

    @Override
    public UserVO realNameVerify(long userId, RealNameVerifyRequest request) {
        User user = requireActiveUser(userId);
        String sid = request.getStudentId().trim();
        if (sid.length() < 6 || sid.length() > 20) {
            throw BizException.badRequest("学号格式不正确");
        }
        user.setStudentId(sid);
        user.setRealNameVerified(true);
        userMapper.updateById(user);
        return VoAssembler.toUserVO(user);
    }
}
