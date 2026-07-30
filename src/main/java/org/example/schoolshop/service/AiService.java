package org.example.schoolshop.service;

import org.example.schoolshop.dto.req.AiChatRequest;
import org.example.schoolshop.dto.req.FeedFeedbackRequest;

import java.util.Map;

public interface AiService {
    Map<String, Object> weekSchedule(long userId);
    Map<String, Object> todaySchedule(long userId);
    Map<String, Object> chat(long userId, AiChatRequest request);
    Map<String, Object> feed(long userId, Integer limit);
    Map<String, Object> feedback(long userId, FeedFeedbackRequest request);
}
