package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedeemRequest {
    @NotNull
    @Min(1)
    private Integer amount;
    private String remark;
}
