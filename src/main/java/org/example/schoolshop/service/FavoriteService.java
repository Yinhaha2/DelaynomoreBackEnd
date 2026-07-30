package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.FavoriteToggleRequest;
import org.example.schoolshop.dto.req.ReportRequest;

import java.util.Map;

public interface FavoriteService {
    Map<String, Object> list(long userId, String type);
    Map<String, Object> toggle(long userId, FavoriteToggleRequest request);
}
