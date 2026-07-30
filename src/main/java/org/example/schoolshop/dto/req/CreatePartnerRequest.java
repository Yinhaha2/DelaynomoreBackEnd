package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreatePartnerRequest {
    @NotBlank
    private String title;
    private String description;
    @NotBlank
    private String category;
    private List<String> tags;
    private String timeText;
    private String location;
    @Min(2)
    private Integer needCount;
}
