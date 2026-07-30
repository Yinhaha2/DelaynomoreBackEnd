package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.*;
import org.example.schoolshop.dto.req.AuditRequest;
import org.example.schoolshop.dto.req.PointsAdjustRequest;
import org.example.schoolshop.mapper.*;
import org.example.schoolshop.service.AdminService;
import org.example.schoolshop.service.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PostMapper postMapper;
    private final MaterialMapper materialMapper;
    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final PointsService pointsService;

    @Override
    public Map<String, Object> listPosts(Integer status, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<Post>().eq(Post::getIsDeleted, 0)
                .orderByDesc(Post::getCreatedAt);
        if (status != null) {
            qw.eq(Post::getStatus, status);
        }
        Page<Post> data = postMapper.selectPage(new Page<>(p, ps), qw);
        Map<String, Object> result = new HashMap<>();
        result.put("list", data.getRecords());
        result.put("total", data.getTotal());
        return result;
    }

    @Override
    @Transactional
    public void auditPost(long postId, AuditRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
        }
        post.setStatus(Boolean.TRUE.equals(request.getPass()) ? 1 : 2);
        postMapper.updateById(post);
    }

    @Override
    public Map<String, Object> listMaterials(Integer status, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<Material>().orderByDesc(Material::getCreatedAt);
        if (status != null) {
            qw.eq(Material::getStatus, status);
        }
        Page<Material> data = materialMapper.selectPage(new Page<>(p, ps), qw);
        Map<String, Object> result = new HashMap<>();
        result.put("list", data.getRecords());
        result.put("total", data.getTotal());
        return result;
    }

    @Override
    @Transactional
    public void auditMaterial(long materialId, AuditRequest request) {
        Material m = materialMapper.selectById(materialId);
        if (m == null) {
            throw BizException.notFound("资料不存在");
        }
        m.setStatus(Boolean.TRUE.equals(request.getPass()) ? 1 : 2);
        materialMapper.updateById(m);
    }

    @Override
    public Map<String, Object> listReports(Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Page<Report> data = reportMapper.selectPage(new Page<>(p, ps),
                new LambdaQueryWrapper<Report>().orderByDesc(Report::getCreatedAt));
        Map<String, Object> result = new HashMap<>();
        result.put("list", data.getRecords());
        result.put("total", data.getTotal());
        return result;
    }

    @Override
    @Transactional
    public void handleReport(long reportId, AuditRequest request) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw BizException.notFound("举报不存在");
        }
        report.setStatus(Boolean.TRUE.equals(request.getPass()) ? "resolved" : "rejected");
        reportMapper.updateById(report);
    }

    @Override
    public Map<String, Object> listUsers(Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Page<User> data = userMapper.selectPage(new Page<>(p, ps),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
        Map<String, Object> result = new HashMap<>();
        result.put("list", data.getRecords());
        result.put("total", data.getTotal());
        return result;
    }

    @Override
    @Transactional
    public void banUser(long userId, boolean banned) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        user.setStatus(banned ? 1 : 0);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public Map<String, Object> adjustPoints(long userId, PointsAdjustRequest request) {
        if (request.getAmount() == null || request.getAmount() == 0) {
            throw BizException.badRequest("调账金额不能为 0");
        }
        String remark = request.getRemark() != null ? request.getRemark() : "管理员调账";
        if (request.getAmount() > 0) {
            pointsService.addIncome(userId, request.getAmount(), remark, "admin", null);
        } else {
            pointsService.deduct(userId, -request.getAmount(), remark, "admin", null);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("balance", pointsService.availableBalance(userId));
        return data;
    }
}
