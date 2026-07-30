package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteToggleRequest {
    @NotBlank
    private String type;
    @NotNull
    private Long targetId;
}
