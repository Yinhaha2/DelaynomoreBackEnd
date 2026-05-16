package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long id;
    private Long senderId;
    private String content;
    private String type;
    private LocalDateTime createdAt;
}
