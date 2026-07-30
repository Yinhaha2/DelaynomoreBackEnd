package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RealNameVerifyRequest {
    @NotBlank
    private String realName;
    @NotBlank
    private String studentId;
}
