package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_material")
public class UserMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long materialId;
    private LocalDateTime createdAt;
}
