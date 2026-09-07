package com.agentcrawler.link;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MagnetLinkParser {

    private static final Pattern BTIH = Pattern.compile("(?i)xt=urn:btih:([a-z0-9]+)");

    private MagnetLinkParser() {
    }

    public static MagnetInfo parse(String magnet) {
        if (magnet == null || magnet.isBlank()) {
            return new MagnetInfo("", "", Map.of());
        }
        Map<String, String> params = parseParams(magnet);
        String hash = params.getOrDefault("xt", "");
        Matcher matcher = BTIH.matcher(magnet);
        if (matcher.find()) {
            hash = matcher.group(1).toLowerCase(Locale.ROOT);
        } else if (hash.toLowerCase(Locale.ROOT).startsWith("urn:btih:")) {
            hash = hash.substring("urn:btih:".length()).toLowerCase(Locale.ROOT);
        }
        String displayName = params.getOrDefault("dn", "");
        return new MagnetInfo(hash, displayName, params);
    }

    private static Map<String, String> parseParams(String magnet) {
        Map<String, String> params = new LinkedHashMap<>();
        int queryStart = magnet.indexOf('?');
        if (queryStart < 0 || queryStart >= magnet.length() - 1) {
            return params;
        }
        for (String pair : magnet.substring(queryStart + 1).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    public record MagnetInfo(String infoHash, String displayName, Map<String, String> params) {
        public boolean hasHash() {
            return infoHash != null && !infoHash.isBlank();
        }
    }
}
