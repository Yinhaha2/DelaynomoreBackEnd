package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("partner_member")
public class PartnerMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partnerId;
    private Long userId;
    private LocalDateTime createdAt;
}
