package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.domain.Report;
import org.example.schoolshop.dto.req.ReportRequest;
import org.example.schoolshop.mapper.ReportMapper;
import org.example.schoolshop.service.ReportService;
import org.example.schoolshop.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final UserService userService;

    @Override
    @Transactional
    public Map<String, Object> create(long userId, ReportRequest request) {
        userService.requireActiveUser(userId);
        Report report = new Report();
        report.setUserId(userId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason());
        report.setDetail(request.getDetail() != null ? request.getDetail() : "");
        report.setStatus("pending");
        reportMapper.insert(report);
        Map<String, Object> data = new HashMap<>();
        data.put("id", report.getId());
        data.put("status", report.getStatus());
        return data;
    }
}
