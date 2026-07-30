package org.example.schoolshop.dto.req;

import lombok.Data;

@Data
public class AuditRequest {
    private Boolean pass;
    private String reason;
}
