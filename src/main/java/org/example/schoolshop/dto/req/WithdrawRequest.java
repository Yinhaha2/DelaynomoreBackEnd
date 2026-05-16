package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WithdrawRequest {
    @NotNull
    @Min(100)
    private Integer amount;
}
