package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_notification")
public class ActivityNotification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long actorId;
    private String type;
    private Long postId;
    private String commentText;
    private String postThumbnail;
    @TableField("`read`")
    private Integer read;
    private LocalDateTime createdAt;
}
