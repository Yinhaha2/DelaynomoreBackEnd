package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.req.FavoriteToggleRequest;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.FavoriteService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final PostMapper postMapper;
    private final MaterialMapper materialMapper;
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;
    private final UserService userService;

    @Override
    public Map<String, Object> list(long userId, String type) {
        userService.requireActiveUser(userId);
        LambdaQueryWrapper<Favorite> qw = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).orderByDesc(Favorite::getCreatedAt);
        if (StringUtils.hasText(type) && !"all".equals(type)) {
            qw.eq(Favorite::getTargetType, type);
        }
        List<Map<String, Object>> list = favoriteMapper.selectList(qw).stream()
                .map(this::toItem).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> toggle(long userId, FavoriteToggleRequest request) {
        userService.requireActiveUser(userId);
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, request.getType())
                .eq(Favorite::getTargetId, request.getTargetId()));
        boolean favorited;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            favorited = false;
        } else {
            Favorite f = new Favorite();
            f.setUserId(userId);
            f.setTargetType(request.getType());
            f.setTargetId(request.getTargetId());
            favoriteMapper.insert(f);
            favorited = true;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("favorited", favorited);
        data.put("type", request.getType());
        data.put("targetId", request.getTargetId());
        return data;
    }

    private Map<String, Object> toItem(Favorite f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("type", f.getTargetType());
        m.put("targetId", f.getTargetId());
        m.put("createdAt", f.getCreatedAt());
        switch (f.getTargetType()) {
            case "post" -> {
                Post p = postMapper.selectById(f.getTargetId());
                if (p == null) return null;
                m.put("title", p.getContent().length() > 30 ? p.getContent().substring(0, 30) + "..." : p.getContent());
                m.put("cover", p.getImages() != null && !p.getImages().isEmpty() ? p.getImages().get(0) : "");
            }
            case "material" -> {
                Material mat = materialMapper.selectById(f.getTargetId());
                if (mat == null) return null;
                m.put("title", mat.getTitle());
                m.put("cover", mat.getCoverUrl());
            }
            case "task" -> {
                Task t = taskMapper.selectById(f.getTargetId());
                if (t == null) return null;
                m.put("title", t.getTitle());
                m.put("cover", "");
            }
            case "course" -> {
                Course c = courseMapper.selectById(f.getTargetId());
                if (c == null) return null;
                m.put("title", c.getName());
                m.put("cover", c.getCover());
            }
            default -> {
                m.put("title", "");
                m.put("cover", "");
            }
        }
        return m;
    }
}
