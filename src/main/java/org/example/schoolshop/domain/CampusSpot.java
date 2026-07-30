package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("campus_spot")
public class CampusSpot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private String zone;
    private Integer x;
    private Integer y;
    private String category;
    private Integer hot;
    private Integer taskCount;
    @TableField("`desc`")
    private String desc;
}
