package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class TradeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String type;
    private Long bizId;
    private String title;
    private Integer amount;
    private Integer status;
    private String outTradeNo;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
