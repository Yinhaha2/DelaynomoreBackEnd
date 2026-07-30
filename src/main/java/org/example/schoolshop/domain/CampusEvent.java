package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "campus_event", autoResultMap = true)
public class CampusEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String cover;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String category;
    private Integer capacity;
    private Integer joinedCount;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String description;
    private LocalDateTime createdAt;
}
