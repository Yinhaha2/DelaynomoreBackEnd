package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostItemVO {
    private Long id;
    private Long userId;
    private UserBriefVO user;
    private Integer categoryId;
    private String categoryName;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean liked;
    private Integer status;
    private LocalDateTime createdAt;
    private List<CommentVO> comments;
}
