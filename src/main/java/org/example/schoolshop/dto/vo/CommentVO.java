package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private UserBriefVO user;
    private String content;
    private LocalDateTime createdAt;
}
