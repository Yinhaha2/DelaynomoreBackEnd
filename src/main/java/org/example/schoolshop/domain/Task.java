package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "task", autoResultMap = true)
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long publisherId;
    private Long acceptorId;
    private String title;
    private String description;
    private String location;
    private Long fromSpotId;
    private Long toSpotId;
    private Integer rewardAmount;
    private Integer status;
    private String category;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String deliveryNote;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> deliveryImages;
    @Version
    private Integer version;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt;
}
