package org.example.schoolshop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("search_hot")
public class SearchHot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private Integer heat;
}
