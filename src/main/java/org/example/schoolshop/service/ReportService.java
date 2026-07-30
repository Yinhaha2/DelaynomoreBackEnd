package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.ReportRequest;

import java.util.Map;

public interface ReportService {
    Map<String, Object> create(long userId, ReportRequest request);
}
