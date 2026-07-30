package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.Partner;
import org.example.schoolshop.domain.PartnerMember;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.CreatePartnerRequest;
import org.example.schoolshop.mapper.PartnerMapper;
import org.example.schoolshop.mapper.PartnerMemberMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.PartnerService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerServiceImpl implements PartnerService {

    private final PartnerMapper partnerMapper;
    private final PartnerMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final ContentSecurityService contentSecurityService;

    @Override
    public Map<String, Object> list(String category, String keyword) {
        LambdaQueryWrapper<Partner> qw = new LambdaQueryWrapper<Partner>()
                .eq(Partner::getStatus, "open").orderByDesc(Partner::getCreatedAt);
        if (StringUtils.hasText(category) && !"all".equals(category)) {
            qw.eq(Partner::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Partner::getTitle, keyword).or().like(Partner::getDescription, keyword));
        }
        List<Map<String, Object>> list = partnerMapper.selectList(qw).stream()
                .map(this::toMap).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> create(long userId, CreatePartnerRequest request) {
        userService.requireActiveUser(userId);
        contentSecurityService.checkText(request.getTitle());
        contentSecurityService.checkText(request.getDescription());
        Partner p = new Partner();
        p.setPublisherId(userId);
        p.setTitle(request.getTitle());
        p.setDescription(request.getDescription());
        p.setCategory(request.getCategory());
        p.setTags(request.getTags());
        p.setTimeText(request.getTimeText());
        p.setLocation(request.getLocation());
        p.setNeedCount(request.getNeedCount() != null ? request.getNeedCount() : 2);
        p.setJoinedCount(1);
        p.setStatus("open");
        partnerMapper.insert(p);
        PartnerMember member = new PartnerMember();
        member.setPartnerId(p.getId());
        member.setUserId(userId);
        memberMapper.insert(member);
        Map<String, Object> data = toMap(p);
        data.put("status", p.getStatus());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> join(long userId, long partnerId) {
        userService.requireActiveUser(userId);
        Partner p = partnerMapper.selectById(partnerId);
        if (p == null || !"open".equals(p.getStatus())) {
            throw BizException.notFound("组局不存在");
        }
        if (memberMapper.selectCount(new LambdaQueryWrapper<PartnerMember>()
                .eq(PartnerMember::getPartnerId, partnerId).eq(PartnerMember::getUserId, userId)) > 0) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", partnerId);
            data.put("joined", true);
            return data;
        }
        if (p.getJoinedCount() >= p.getNeedCount()) {
            throw BizException.unprocessable("人数已满");
        }
        PartnerMember member = new PartnerMember();
        member.setPartnerId(partnerId);
        member.setUserId(userId);
        memberMapper.insert(member);
        p.setJoinedCount(p.getJoinedCount() + 1);
        if (p.getJoinedCount() >= p.getNeedCount()) {
            p.setStatus("full");
        }
        partnerMapper.updateById(p);
        Map<String, Object> data = new HashMap<>();
        data.put("id", partnerId);
        data.put("joined", true);
        return data;
    }

    private Map<String, Object> toMap(Partner p) {
        User publisher = userMapper.selectById(p.getPublisherId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("description", p.getDescription());
        m.put("category", p.getCategory());
        m.put("tags", p.getTags());
        m.put("timeText", p.getTimeText());
        m.put("location", p.getLocation());
        m.put("needCount", p.getNeedCount());
        m.put("joinedCount", p.getJoinedCount());
        m.put("publisher", VoAssembler.toUserBrief(publisher));
        m.put("status", p.getStatus());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }
}
