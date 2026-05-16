package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityVO {
    private Long id;
    private String type;
    private UserBriefVO user;
    private String content;
    private Long postId;
    private String commentText;
    private String postThumbnail;
    private Boolean read;
    private Boolean followedBack;
    private LocalDateTime createdAt;
}
