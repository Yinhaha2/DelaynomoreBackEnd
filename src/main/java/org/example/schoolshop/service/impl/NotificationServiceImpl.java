package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.ActivityNotification;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.domain.UserFollow;
import org.example.schoolshop.dto.vo.ActivityVO;
import org.example.schoolshop.mapper.ActivityNotificationMapper;
import org.example.schoolshop.mapper.ConversationMapper;
import org.example.schoolshop.mapper.UserFollowMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.NotificationService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final ActivityNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ConversationMapper conversationMapper;

    @Override
    public Map<String, Object> listActivities(long userId, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Page<ActivityNotification> pageData = notificationMapper.selectPage(new Page<>(p, ps),
                new LambdaQueryWrapper<ActivityNotification>()
                        .eq(ActivityNotification::getUserId, userId)
                        .orderByDesc(ActivityNotification::getCreatedAt));
        List<ActivityVO> list = pageData.getRecords().stream().map(n -> {
            User actor = userMapper.selectById(n.getActorId());
            ActivityVO vo = new ActivityVO();
            vo.setId(n.getId());
            vo.setType(n.getType());
            vo.setUser(VoAssembler.toUserBrief(actor));
            vo.setContent(contentForType(n.getType()));
            vo.setPostId(n.getPostId());
            vo.setCommentText(n.getCommentText());
            vo.setPostThumbnail(n.getPostThumbnail());
            vo.setRead(n.getRead() != null && n.getRead() == 1);
            vo.setCreatedAt(n.getCreatedAt());
            if ("FOLLOW".equals(n.getType())) {
                long cnt = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getFolloweeId, n.getActorId()));
                vo.setFollowedBack(cnt > 0);
            }
            return vo;
        }).collect(Collectors.toList());
        long unread = notificationMapper.selectCount(new LambdaQueryWrapper<ActivityNotification>()
                .eq(ActivityNotification::getUserId, userId).eq(ActivityNotification::getRead, 0));
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("unreadCount", unread);
        return data;
    }

    @Override
    public Map<String, Integer> unreadCount(long userId) {
        long activityUnread = notificationMapper.selectCount(new LambdaQueryWrapper<ActivityNotification>()
                .eq(ActivityNotification::getUserId, userId).eq(ActivityNotification::getRead, 0));
        long messageUnread = conversationMapper.selectList(new LambdaQueryWrapper<org.example.schoolshop.domain.Conversation>()
                .eq(org.example.schoolshop.domain.Conversation::getUserId, userId))
                .stream().mapToInt(c -> c.getUnread() != null ? c.getUnread() : 0).sum();
        Map<String, Integer> data = new HashMap<>();
        data.put("count", (int) (activityUnread + messageUnread));
        return data;
    }

    @Override
    @Transactional
    public void markRead(long userId, long activityId) {
        ActivityNotification n = notificationMapper.selectById(activityId);
        if (n == null || !n.getUserId().equals(userId)) {
            throw BizException.notFound("通知不存在");
        }
        n.setRead(1);
        notificationMapper.updateById(n);
    }

    private String contentForType(String type) {
        return switch (type) {
            case "FOLLOW" -> "开始关注了你";
            case "LIKE_POST" -> "赞了你的帖子";
            case "COMMENT_POST" -> "评论了你的帖子";
            default -> "";
        };
    }
}
