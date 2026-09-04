package com.agentcrawler.api;

import com.agentcrawler.model.ChatStreamRequest;
import com.agentcrawler.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(
            @Valid @RequestBody ChatStreamRequest request,
            @RequestHeader(value = "Accept", required = false) String accept
    ) {
        if (accept != null && !accept.contains("text/event-stream")) {
            return ResponseEntity.status(406).build();
        }

        StreamingResponseBody body = outputStream -> chatService.streamChat(
                request,
                frame -> {
                    try {
                        outputStream.write(frame.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
        );

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }
}
