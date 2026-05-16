package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {
    @NotBlank(message = "code 不能为空")
    private String code;
}
