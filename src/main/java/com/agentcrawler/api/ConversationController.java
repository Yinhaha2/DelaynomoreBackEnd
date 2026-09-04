package com.agentcrawler.api;

import com.agentcrawler.model.CreateConversationResponse;
import com.agentcrawler.service.ConversationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public CreateConversationResponse createConversation() {
        return conversationService.create();
    }
}
