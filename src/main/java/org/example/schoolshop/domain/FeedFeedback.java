package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feed_feedback")
public class FeedFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String itemId;
    private String action;
    private LocalDateTime createdAt;
}
