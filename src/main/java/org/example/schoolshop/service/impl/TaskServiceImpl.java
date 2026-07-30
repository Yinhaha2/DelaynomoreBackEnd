package org.example.schoolshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.common.CategoryConstants;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.domain.Task;
import org.example.schoolshop.domain.TradeOrder;
import org.example.schoolshop.domain.User;
import org.example.schoolshop.dto.req.CreateTaskRequest;
import org.example.schoolshop.dto.req.DeliverTaskRequest;
import org.example.schoolshop.dto.vo.PayParamsVO;
import org.example.schoolshop.dto.vo.TaskItemVO;
import org.example.schoolshop.mapper.TaskMapper;
import org.example.schoolshop.mapper.TradeOrderMapper;
import org.example.schoolshop.mapper.UserMapper;
import org.example.schoolshop.service.ContentSecurityService;
import org.example.schoolshop.service.PointsService;
import org.example.schoolshop.service.TaskService;
import org.example.schoolshop.service.UserService;
import org.example.schoolshop.util.VoAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TradeOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final PointsService pointsService;
    private final ContentSecurityService contentSecurityService;

    @Override
    public PageResult<TaskItemVO> list(Integer page, Integer pageSize, Integer status, String category,
                                       String keyword, String sort) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        int st = status == null ? 1 : status;
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>().eq(Task::getStatus, st);
        if (StringUtils.hasText(category)) {
            qw.eq(Task::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Task::getTitle, keyword).or().like(Task::getDescription, keyword)
                    .or().like(Task::getLocation, keyword));
        }
        if ("reward".equals(sort)) {
            qw.orderByDesc(Task::getRewardAmount);
        } else {
            qw.orderByDesc(Task::getCreatedAt);
        }
        Page<Task> pageData = taskMapper.selectPage(new Page<>(p, ps), qw);
        List<TaskItemVO> list = pageData.getRecords().stream().map(this::toItem).collect(Collectors.toList());
        PageResult<TaskItemVO> result = new PageResult<>();
        result.setList(list);
        result.setTotal(pageData.getTotal());
        result.setPage(pageData.getCurrent());
        result.setPageSize(pageData.getSize());
        result.setHasMore(pageData.getCurrent() * pageData.getSize() < pageData.getTotal());
        return result;
    }

    @Override
    public TaskItemVO detail(Long taskId, Long currentUserId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return toItem(task);
    }

    @Override
    @Transactional
    public Map<String, Object> create(long userId, CreateTaskRequest request) {
        User user = userService.requireActiveUser(userId);
        if (!Boolean.TRUE.equals(user.getRealNameVerified())) {
            throw BizException.unprocessable("请先完成实名认证");
        }
        contentSecurityService.checkText(request.getTitle());
        contentSecurityService.checkText(request.getDescription());
        if (request.getRewardAmount() < 50) {
            throw BizException.badRequest("悬赏积分最低 50");
        }

        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory() : "other";
        Task task = new Task();
        task.setPublisherId(userId);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setLocation(request.getLocation());
        task.setFromSpotId(request.getFromSpotId());
        task.setRewardAmount(request.getRewardAmount());
        task.setStatus(1);
        task.setCategory(category);
        task.setTags(request.getTags() != null ? request.getTags() : CategoryConstants.defaultTaskTags(category));
        task.setDeadline(request.getDeadline() != null ? request.getDeadline() : LocalDateTime.now().plusDays(7));
        task.setVersion(0);
        taskMapper.insert(task);

        pointsService.freeze(userId, request.getRewardAmount(),
                "发布悬赏冻结：" + request.getTitle(), "task", task.getId());

        TradeOrder order = new TradeOrder();
        order.setOrderNo("O" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setType("task");
        order.setBizId(task.getId());
        order.setTitle(task.getTitle());
        order.setAmount(task.getRewardAmount());
        order.setStatus(1);
        order.setOutTradeNo("TASK_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8));
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", 1);
        data.put("rewardAmount", task.getRewardAmount());
        return data;
    }

    @Override
    public PayParamsVO pay(long userId, long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getPublisherId().equals(userId)) {
            throw BizException.badRequest("任务状态不允许支付");
        }
        PayParamsVO vo = new PayParamsVO();
        vo.setPackageValue("mock");
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Object> accept(long userId, long taskId) {
        userService.requireActiveUser(userId);
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 1) {
            throw BizException.conflict("任务已被接单");
        }
        if (task.getPublisherId().equals(userId)) {
            throw BizException.unprocessable("不能接自己发布的任务");
        }
        LambdaUpdateWrapper<Task> uw = new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .eq(Task::getStatus, 1)
                .set(Task::getStatus, 2)
                .set(Task::getAcceptorId, userId)
                .set(Task::getAcceptedAt, LocalDateTime.now());
        int rows = taskMapper.update(null, uw);
        if (rows == 0) {
            throw BizException.conflict("任务已被接单");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("status", 2);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> deliver(long userId, long taskId, DeliverTaskRequest request) {
        if (request.getDeliveryImages() == null || request.getDeliveryImages().isEmpty()) {
            throw BizException.badRequest("请上传交付凭证");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 2 || !java.util.Objects.equals(userId, task.getAcceptorId())) {
            throw BizException.badRequest("当前状态不可交付");
        }
        task.setStatus(3);
        task.setDeliveryNote(request.getDeliveryNote());
        task.setDeliveryImages(request.getDeliveryImages());
        task.setDeliveredAt(LocalDateTime.now());
        taskMapper.updateById(task);
        Map<String, Object> data = new HashMap<>();
        data.put("status", 3);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> confirm(long userId, long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 3 || !java.util.Objects.equals(userId, task.getPublisherId())) {
            throw BizException.badRequest("当前状态不可验收");
        }
        task.setStatus(4);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        pointsService.settleTaskReward(userId, task.getAcceptorId(), task.getRewardAmount(), taskId);
        Map<String, Object> data = new HashMap<>();
        data.put("status", 4);
        return data;
    }

    @Override
    public PageResult<TaskItemVO> myTasks(long userId, String type, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<>();
        if ("accepted".equals(type)) {
            qw.eq(Task::getAcceptorId, userId);
        } else {
            qw.eq(Task::getPublisherId, userId);
        }
        qw.orderByDesc(Task::getCreatedAt);
        Page<Task> pageData = taskMapper.selectPage(new Page<>(p, ps), qw);
        return PageResult.of(pageData.convert(this::toItem));
    }

    private TaskItemVO toItem(Task task) {
        User publisher = userMapper.selectById(task.getPublisherId());
        User acceptor = task.getAcceptorId() != null ? userMapper.selectById(task.getAcceptorId()) : null;
        return VoAssembler.toTaskItem(task, publisher, acceptor);
    }
}
