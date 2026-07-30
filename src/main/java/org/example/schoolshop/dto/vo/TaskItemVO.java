package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskItemVO {
    private Long id;
    private String title;
    private String description;
    private String location;
    private Integer rewardAmount;
    private Integer status;
    private String category;
    private List<String> tags;
    private UserBriefVO publisher;
    private UserBriefVO acceptor;
    private String deliveryNote;
    private List<String> deliveryImages;
    private Long fromSpotId;
    private Long toSpotId;
    private LocalDateTime createdAt;
    private LocalDateTime deadline;
}
