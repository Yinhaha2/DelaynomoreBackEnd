package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.Material;
import org.example.schoolshop.domain.Post;
import org.example.schoolshop.domain.Task;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.mapper.MaterialMapper;
import org.example.schoolshop.mapper.PostMapper;
import org.example.schoolshop.mapper.TaskMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.RankingService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final MaterialMapper materialMapper;
    private final PostMapper postMapper;

    @Override
    public Map<String, Object> list(String type) {
        String t = type != null ? type : "helper";
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getStatus, 0));
        List<Map<String, Object>> scored = new ArrayList<>();
        for (User user : users) {
            int score = switch (t) {
                case "seller" -> materialMapper.selectList(new LambdaQueryWrapper<Material>()
                        .eq(Material::getUserId, user.getId())).stream()
                        .mapToInt(m -> m.getSoldCount() != null ? m.getSoldCount() : 0).sum();
                case "creator" -> postMapper.selectCount(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, user.getId()).eq(Post::getStatus, 1).eq(Post::getIsDeleted, 0)).intValue();
                default -> taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                        .eq(Task::getAcceptorId, user.getId()).eq(Task::getStatus, 4)).intValue();
            };
            if (score <= 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", user.getId());
            item.put("nickname", user.getNickname());
            item.put("avatar", user.getAvatar());
            item.put("score", score);
            item.put("label", labelFor(t, score));
            scored.add(item);
        }
        scored.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));
        String[] medals = {"🥇", "🥈", "🥉"};
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < Math.min(10, scored.size()); i++) {
            Map<String, Object> item = scored.get(i);
            item.put("rank", i + 1);
            if (i < 3) {
                item.put("badge", medals[i]);
            }
            list.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", t);
        data.put("list", list);
        return data;
    }

    private String labelFor(String type, int score) {
        return switch (type) {
            case "seller" -> "已售 " + score + " 份资料";
            case "creator" -> "发布 " + score + " 篇帖子";
            default -> "本周帮 " + score + " 单";
        };
    }
}
