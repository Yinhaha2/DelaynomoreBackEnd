package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderItemVO {
    private Long id;
    private String orderNo;
    private String type;
    private String title;
    private Integer amount;
    private String unit;
    private String status;
    private LocalDateTime createdAt;
}
