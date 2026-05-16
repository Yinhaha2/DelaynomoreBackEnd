package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.vo.ActivityVO;

import java.util.Map;

public interface NotificationService {

    Map<String, Object> listActivities(long userId, Integer page, Integer pageSize);

    Map<String, Integer> unreadCount(long userId);

    void markRead(long userId, long activityId);
}
