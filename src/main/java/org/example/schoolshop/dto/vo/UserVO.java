package org.example.schoolshop.dto.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String studentId;
    private Boolean realNameVerified;
    private Integer status;
    private Integer walletBalance;
    private String bio;
}
