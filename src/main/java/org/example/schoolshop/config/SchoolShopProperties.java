package org.example.schoolshop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "schoolshop")
public class SchoolShopProperties {

    private Wechat wechat = new Wechat();
    private Oss oss = new Oss();
    private Ai ai = new Ai();
    private Admin admin = new Admin();
    private Security security = new Security();
    private TaskJob taskJob = new TaskJob();

    @Data
    public static class Wechat {
        /** 小程序 AppID，留空则使用 mock 登录 */
        private String appId = "";
        /** 小程序 AppSecret */
        private String appSecret = "";
        /** 未配置微信时是否允许 mock 登录（开发联调） */
        private boolean mockEnabled = true;
    }

    @Data
    public static class Oss {
        /** 是否启用 OSS（false 时上传走本地 mock URL） */
        private boolean enabled = false;
        /** 如 oss-cn-hangzhou.aliyuncs.com */
        private String endpoint = "";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        /** 帖子图片等公共读 Bucket */
        private String publicBucket = "";
        /** 资料文件私有 Bucket */
        private String privateBucket = "";
        /** 公共读访问域名，如 https://your-bucket.oss-cn-hangzhou.aliyuncs.com */
        private String publicBaseUrl = "";
    }

    @Data
    public static class Ai {
        private boolean enabled = false;
        /** OpenAI 兼容 API Key */
        private String apiKey = "";
        /** 如 https://api.openai.com/v1 或国内中转地址 */
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
    }

    @Data
    public static class Admin {
        /** 管理端 Token，请求头 X-Admin-Token；留空则禁用 Admin 接口 */
        private String token = "";
    }

    @Data
    public static class Security {
        /** 内容安全：local（本地敏感词）| wechat（微信 msgSecCheck，需配置微信） */
        private String contentCheckMode = "local";
        /** 写接口限流：每用户每分钟最大次数 */
        private int rateLimitPerMinute = 30;
    }

    @Data
    public static class TaskJob {
        private boolean enabled = true;
        /** 待验收超过该小时数自动确认 */
        private int autoConfirmHours = 48;
        /** 招募超时自动取消并解冻积分 */
        private boolean cancelExpiredRecruiting = true;
    }

    public boolean isWechatConfigured() {
        return wechat.getAppId() != null && !wechat.getAppId().isBlank()
                && wechat.getAppSecret() != null && !wechat.getAppSecret().isBlank();
    }

    public boolean isOssConfigured() {
        return oss.isEnabled()
                && oss.getEndpoint() != null && !oss.getEndpoint().isBlank()
                && oss.getAccessKeyId() != null && !oss.getAccessKeyId().isBlank()
                && oss.getAccessKeySecret() != null && !oss.getAccessKeySecret().isBlank();
    }

    public boolean isAiConfigured() {
        return ai.isEnabled()
                && ai.getApiKey() != null && !ai.getApiKey().isBlank();
    }

    public boolean isAdminConfigured() {
        return admin.getToken() != null && !admin.getToken().isBlank();
    }
}
