package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotNull
    private Long peerId;
    @NotBlank
    private String content;
    private String type = "text";
}
