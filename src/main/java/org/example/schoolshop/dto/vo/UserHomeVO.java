package org.example.schoolshop.dto.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserHomeVO {
    private UserProfileVO user;
    private List<PostItemVO> posts;
    private List<MaterialItemVO> materials;

    @Data
    public static class UserProfileVO {
        private Long id;
        private String nickname;
        private String avatar;
        private String bio;
        private Long postCount;
        private Long taskCount;
        private Long materialCount;
        private Boolean followed;
    }
}
