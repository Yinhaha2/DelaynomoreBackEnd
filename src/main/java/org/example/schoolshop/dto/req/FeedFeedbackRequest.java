package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedFeedbackRequest {
    @NotBlank
    private String itemId;
    @NotBlank
    private String action;
}
