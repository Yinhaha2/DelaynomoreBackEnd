package org.example.schoolshop.service;

import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.dto.req.CreateTaskRequest;
import org.example.schoolshop.dto.req.DeliverTaskRequest;
import org.example.schoolshop.dto.vo.PayParamsVO;
import org.example.schoolshop.dto.vo.TaskItemVO;

import java.util.Map;

public interface TaskService {

    PageResult<TaskItemVO> list(Integer page, Integer pageSize, Integer status, String category,
                               String keyword, String sort);

    TaskItemVO detail(Long taskId, Long currentUserId);

    Map<String, Object> create(long userId, CreateTaskRequest request);

    PayParamsVO pay(long userId, long taskId);

    Map<String, Object> accept(long userId, long taskId);

    Map<String, Object> deliver(long userId, long taskId, DeliverTaskRequest request);

    Map<String, Object> confirm(long userId, long taskId);

    PageResult<TaskItemVO> myTasks(long userId, String type, Integer page, Integer pageSize);

    Map<String, Object> cancel(long userId, long taskId);

    int autoConfirmExpiredTasks();

    int cancelExpiredRecruitingTasks();
}
