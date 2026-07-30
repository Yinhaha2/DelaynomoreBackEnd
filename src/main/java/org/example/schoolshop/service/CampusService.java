package org.example.schoolshop.service;

import java.util.Map;

public interface CampusService {
    Map<String, Object> listSpots(String category, String keyword);
    Map<String, Object> spotDetail(long id);
    Map<String, Object> mapTasks();
    Map<String, Object> listEvents(String month, Long userId);
    Map<String, Object> joinEvent(long userId, long eventId);
}
