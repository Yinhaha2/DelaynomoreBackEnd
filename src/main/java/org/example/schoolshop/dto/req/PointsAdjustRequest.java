package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsAdjustRequest {
    @NotNull
    private Integer amount;
    private String remark;
}
