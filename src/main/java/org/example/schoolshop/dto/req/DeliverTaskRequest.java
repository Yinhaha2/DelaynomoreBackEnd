package org.example.schoolshop.dto.req;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DeliverTaskRequest {
    private String deliveryNote;
    @NotEmpty
    private List<String> deliveryImages;
}
