package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {
    @NotNull
    private Integer categoryId;
    @NotBlank
    private String content;
    private List<String> images;
}
