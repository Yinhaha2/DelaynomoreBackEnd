package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.CreatePartnerRequest;

import java.util.Map;

public interface PartnerService {
    Map<String, Object> list(String category, String keyword);
    Map<String, Object> create(long userId, CreatePartnerRequest request);
    Map<String, Object> join(long userId, long partnerId);
}
