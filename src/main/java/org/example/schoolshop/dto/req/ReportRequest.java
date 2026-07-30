package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportRequest {
    @NotBlank
    private String targetType;
    @NotNull
    private Long targetId;
    @NotBlank
    private String reason;
    private String detail;
}
