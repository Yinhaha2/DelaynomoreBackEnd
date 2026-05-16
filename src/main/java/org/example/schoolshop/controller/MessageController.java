package org.example.schoolshop.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.PageResult;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.dto.req.SendMessageRequest;
import org.example.schoolshop.dto.vo.ConversationVO;
import org.example.schoolshop.dto.vo.MessageVO;
import org.example.schoolshop.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public Result<Map<String, List<ConversationVO>>> conversations() {
        return Result.ok(messageService.conversations(UserContext.requireUserId()));
    }

    @GetMapping("/{peerId}")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable Long peerId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        return Result.ok(messageService.chatMessages(UserContext.requireUserId(), peerId, page, pageSize));
    }

    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody SendMessageRequest request) {
        return Result.ok(messageService.send(UserContext.requireUserId(), request));
    }
}
