package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialItemVO {
    private Long id;
    private String title;
    private String description;
    private Integer price;
    private String coverUrl;
    private String fileType;
    private String category;
    private Integer soldCount;
    private Boolean owned;
    private LocalDateTime createdAt;
}
