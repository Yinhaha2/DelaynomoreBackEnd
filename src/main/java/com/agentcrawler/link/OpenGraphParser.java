package com.agentcrawler.link;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class OpenGraphParser {

    private OpenGraphParser() {
    }

    public static OpenGraphSnapshot parse(String html) {
        if (html == null || html.isBlank()) {
            return OpenGraphSnapshot.empty();
        }
        Document doc = Jsoup.parse(html);
        String title = firstNonBlank(
                meta(doc, "og:title"),
                meta(doc, "twitter:title"),
                doc.title()
        );
        String description = firstNonBlank(
                meta(doc, "og:description"),
                meta(doc, "description"),
                meta(doc, "twitter:description")
        );
        String image = firstNonBlank(meta(doc, "og:image"), meta(doc, "twitter:image"));
        String siteName = firstNonBlank(meta(doc, "og:site_name"));
        return new OpenGraphSnapshot(title.trim(), description.trim(), image.trim(), siteName.trim());
    }

    private static String meta(Document doc, String property) {
        String byProperty = doc.select("meta[property=" + property + "]").attr("content");
        if (byProperty != null && !byProperty.isBlank()) {
            return byProperty;
        }
        String byName = doc.select("meta[name=" + property + "]").attr("content");
        return byName == null ? "" : byName;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
