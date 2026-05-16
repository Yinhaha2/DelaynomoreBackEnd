package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Integer price;
    private String coverUrl;
    private String fileKey;
    private String fileName;
    private String fileType;
    private String category;
    private Integer status;
    private Integer soldCount;
    private LocalDateTime createdAt;
}
