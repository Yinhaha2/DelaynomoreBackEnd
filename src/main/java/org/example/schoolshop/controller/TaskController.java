package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.CreateTaskRequest;
import org.example.schoolshop.dto.req.DeliverTaskRequest;
import org.example.schoolshop.dto.vo.PayParamsVO;
import org.example.schoolshop.dto.vo.TaskItemVO;
import org.example.schoolshop.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public Result<PageResult<TaskItemVO>> list(
            @RequestParam(defaultValue = "1") Integer status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "time") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(taskService.list(page, pageSize, status, category, keyword, sort));
    }

    @GetMapping("/{id}")
    public Result<TaskItemVO> detail(@PathVariable Long id) {
        return Result.ok(taskService.detail(id, UserContext.getUserId()));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateTaskRequest request) {
        return Result.ok(taskService.create(UserContext.requireUserId(), request));
    }

    @PostMapping("/{id}/pay")
    public Result<PayParamsVO> pay(@PathVariable Long id) {
        return Result.ok(taskService.pay(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/accept")
    public Result<Map<String, Object>> accept(@PathVariable Long id) {
        return Result.ok(taskService.accept(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/deliver")
    public Result<Map<String, Object>> deliver(@PathVariable Long id,
                                               @Valid @RequestBody DeliverTaskRequest request) {
        return Result.ok(taskService.deliver(UserContext.requireUserId(), id, request));
    }

    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id) {
        return Result.ok(taskService.confirm(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id) {
        return Result.ok(taskService.cancel(UserContext.requireUserId(), id));
    }

    @GetMapping("/my/{type}")
    public Result<PageResult<TaskItemVO>> my(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(taskService.myTasks(UserContext.requireUserId(), type, page, pageSize));
    }
}
