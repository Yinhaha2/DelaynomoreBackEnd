package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("schedule_course")
public class ScheduleCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer dayOfWeek;
    private String name;
    private String teacher;
    private String place;
    private String startTime;
    private String endTime;
    private String color;
}
