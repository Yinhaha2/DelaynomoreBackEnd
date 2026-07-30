package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PointsAccountVO {
    private Integer balance;
    private Integer level;
    private String levelName;
    private Integer exp;
    private Integer nextLevelExp;
    private List<PointsRecordVO> records;

    @Data
    public static class PointsRecordVO {
        private Long id;
        private String type;
        private Integer amount;
        private String remark;
        private LocalDateTime createdAt;
    }
}
