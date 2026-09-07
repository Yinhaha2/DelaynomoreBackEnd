package com.agentcrawler.link;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 阻止探测内网 / 本机地址，避免把用户链接变成 SSRF。
 */
public final class LinkSafety {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]"
    );

    private LinkSafety() {
    }

    public static boolean isBlocked(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("magnet:")) {
            return false;
        }
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return true;
            }
            String hostLower = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(hostLower)) {
                return true;
            }
            if (hostLower.endsWith(".local") || hostLower.endsWith(".internal")) {
                return true;
            }
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception ex) {
            return true;
        }
    }
}
