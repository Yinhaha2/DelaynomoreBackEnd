package org.example.schoolshop.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.config.SchoolShopProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatClient {

    private final SchoolShopProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private String cachedToken;
    private Instant tokenExpireAt = Instant.EPOCH;

    public boolean isConfigured() {
        return properties.isWechatConfigured();
    }

    public WxSession code2Session(String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("微信未配置");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}"
                + "&js_code={code}&grant_type=authorization_code";
        String body = restClient.get()
                .uri(url, properties.getWechat().getAppId(), properties.getWechat().getAppSecret(), code)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.warn("jscode2session failed: {}", body);
                throw BizException.unauthorized("微信登录失败");
            }
            WxSession session = new WxSession();
            session.setOpenid(node.path("openid").asText());
            session.setUnionid(node.path("unionid").asText(null));
            session.setSessionKey(node.path("session_key").asText(null));
            return session;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("parse jscode2session response failed", e);
            throw BizException.unauthorized("微信登录失败");
        }
    }

    public void checkText(String content) {
        if (!isConfigured()) {
            return;
        }
        String token = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=" + token;
        try {
            String body = restClient.post()
                    .uri(url)
                    .body(Map.of("content", content))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            int errcode = node.path("errcode").asInt(0);
            if (errcode == 87014) {
                throw BizException.unprocessable("包含敏感词，请修改后重试");
            }
            if (errcode != 0) {
                log.warn("msg_sec_check errcode={} body={}", errcode, body);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("msg_sec_check failed", e);
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(120))) {
            return cachedToken;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
        String body = restClient.get()
                .uri(url, properties.getWechat().getAppId(), properties.getWechat().getAppSecret())
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode")) {
                throw new IllegalStateException("get access_token failed: " + body);
            }
            cachedToken = node.path("access_token").asText();
            int expiresIn = node.path("expires_in").asInt(7200);
            tokenExpireAt = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (Exception e) {
            throw new IllegalStateException("get access_token failed", e);
        }
    }

    @lombok.Data
    public static class WxSession {
        private String openid;
        private String unionid;
        private String sessionKey;
    }
}
