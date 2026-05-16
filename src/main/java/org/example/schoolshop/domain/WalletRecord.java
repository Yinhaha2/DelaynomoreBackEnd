package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wallet_record")
public class WalletRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private Integer amount;
    private String remark;
    private String bizType;
    private Long bizId;
    private LocalDateTime createdAt;
}
