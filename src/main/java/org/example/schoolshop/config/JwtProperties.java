package org.example.schoolshop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "schoolshop.jwt")
public class JwtProperties {

    private String secret = "schoolshop-dev-jwt-secret-key-32bytes!!";
    private int expireHours = 168;
}
