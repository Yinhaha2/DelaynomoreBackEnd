package org.example.schoolshop.service;

import java.util.Map;

public interface CheckinService {
    Map<String, Object> status(long userId);
    Map<String, Object> checkin(long userId);
}
