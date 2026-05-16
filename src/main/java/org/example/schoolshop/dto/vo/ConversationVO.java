package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long id;
    private Long peerId;
    private String peerName;
    private String peerAvatar;
    private String lastMessage;
    private LocalDateTime lastTime;
    private Integer unread;
}
