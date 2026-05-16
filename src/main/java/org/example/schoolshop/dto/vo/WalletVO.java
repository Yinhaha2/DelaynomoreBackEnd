package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WalletVO {
    private Integer balance;
    private List<WalletRecordVO> records;

    @Data
    public static class WalletRecordVO {
        private Long id;
        private String type;
        private Integer amount;
        private String remark;
        private LocalDateTime createdAt;
    }
}
