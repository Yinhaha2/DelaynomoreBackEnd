package com.agentcrawler.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class CacheObjectIds {
    private CacheObjectIds() {}

    static boolean blankKeyword(String keyword) {
        return keyword == null || keyword.trim().isEmpty();
    }

    static String of(String keyword, String site) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
        String normalizedSite = site == null ? "" : site.trim();
        return sha256(normalizedKeyword + "\n" + normalizedSite);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
