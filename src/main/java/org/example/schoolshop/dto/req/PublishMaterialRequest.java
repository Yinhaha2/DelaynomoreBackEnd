package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PublishMaterialRequest {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    @Min(0)
    private Integer price;
    @NotBlank
    private String fileKey;
    private String coverUrl;
    private String category;
}
