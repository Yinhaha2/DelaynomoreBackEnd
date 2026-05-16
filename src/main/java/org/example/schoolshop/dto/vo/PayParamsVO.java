package org.example.schoolshop.dto.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayParamsVO {
    private String timeStamp;
    private String nonceStr;
    @JsonProperty("package")
    private String packageValue;
    private String signType;
    private String paySign;
}
