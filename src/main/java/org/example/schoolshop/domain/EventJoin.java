package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_join")
public class EventJoin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Long userId;
    private LocalDateTime createdAt;
}
