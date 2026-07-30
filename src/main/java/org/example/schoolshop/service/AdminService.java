package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.AuditRequest;
import org.example.schoolshop.dto.req.PointsAdjustRequest;

import java.util.Map;

public interface AdminService {

    Map<String, Object> listPosts(Integer status, Integer page, Integer pageSize);

    void auditPost(long postId, AuditRequest request);

    Map<String, Object> listMaterials(Integer status, Integer page, Integer pageSize);

    void auditMaterial(long materialId, AuditRequest request);

    Map<String, Object> listReports(Integer page, Integer pageSize);

    void handleReport(long reportId, AuditRequest request);

    Map<String, Object> listUsers(Integer page, Integer pageSize);

    void banUser(long userId, boolean banned);

    Map<String, Object> adjustPoints(long userId, PointsAdjustRequest request);
}
