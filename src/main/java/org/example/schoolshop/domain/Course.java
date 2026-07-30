package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@TableName(value = "course", autoResultMap = true)
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String teacher;
    private String college;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer difficulty;
    private Integer useful;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String cover;
}
