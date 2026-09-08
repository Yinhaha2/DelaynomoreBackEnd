package com.agentcrawler.crawler.fallback.silisili;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * SiliSili play-page payload: 9-char prefix + AES/CBC of JSON {@code {url, fenjihtml}}.
 * Ported from SakuraAnime {@code ImomoeJsoupUtils.getDecodeData}.
 */
public final class SilisiliPlayerDecoder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CIPHER = "AES/CBC/PKCS5Padding";

    private SilisiliPlayerDecoder() {}

    public static Optional<DecodedPlay> decode(String rawBody) {
        if (rawBody == null) {
            return Optional.empty();
        }
        String payload = rawBody.trim();
        if (payload.length() < 10 || looksLikeHtml(payload)) {
            return Optional.empty();
        }
        try {
            String prefix = payload.substring(0, 9);
            String cipherText = payload.substring(9);
            String digest = DigestUtils.md5DigestAsHex(prefix.getBytes(StandardCharsets.UTF_8));
            String iv = digest.substring(0, 16);
            String key = digest.substring(16);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
            );
            byte[] decoded = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(json);
            String url = text(node, "url");
            String fenji = text(node, "fenjihtml");
            if (url.isBlank() && fenji.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new DecodedPlay(url, fenji));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeHtml(String payload) {
        String head = payload.substring(0, Math.min(32, payload.length())).toLowerCase();
        return head.contains("<html") || head.contains("<!doctype") || head.contains("<body");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public record DecodedPlay(String url, String fenjiHtml) {}
}
