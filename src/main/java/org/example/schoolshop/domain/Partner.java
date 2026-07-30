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
@TableName(value = "partner", autoResultMap = true)
public class Partner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long publisherId;
    private String title;
    private String description;
    private String category;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String timeText;
    private String location;
    private Integer needCount;
    private Integer joinedCount;
    private String status;
    private LocalDateTime createdAt;
}
