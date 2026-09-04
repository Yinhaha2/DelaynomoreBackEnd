package com.agentcrawler.crawler.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class UrlNormalizer {
    private UrlNormalizer() {}

    public static String normalizeEpisodeUrl(String baseUrl, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        URI base = URI.create(ensureTrailingSlash(baseUrl));
        URI resolved;
        try {
            resolved = base.resolve(trimmed);
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
        try {
            URI normalized = unifyScheme(base, resolved);
            String path = normalized.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                normalized = new URI(
                        normalized.getScheme(),
                        normalized.getAuthority(),
                        path.substring(0, path.length() - 1),
                        normalized.getQuery(),
                        normalized.getFragment()
                );
            }
            if (normalized.getQuery() != null && normalized.getQuery().isEmpty()) {
                normalized = new URI(
                        normalized.getScheme(),
                        normalized.getAuthority(),
                        normalized.getPath(),
                        null,
                        normalized.getFragment()
                );
            }
            return normalized.toString();
        } catch (URISyntaxException ex) {
            return resolved.toString();
        }
    }

    private static URI unifyScheme(URI base, URI target) throws URISyntaxException {
        if (base.getHost() != null
                && base.getHost().equalsIgnoreCase(target.getHost())
                && base.getScheme() != null
                && target.getScheme() != null
                && !base.getScheme().equalsIgnoreCase(target.getScheme())) {
            return new URI(
                    base.getScheme(),
                    target.getUserInfo(),
                    target.getHost(),
                    target.getPort(),
                    target.getPath(),
                    target.getQuery(),
                    target.getFragment()
            );
        }
        return target;
    }

    private static String ensureTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
