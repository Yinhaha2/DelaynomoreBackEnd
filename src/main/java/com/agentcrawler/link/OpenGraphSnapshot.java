package com.agentcrawler.link;

public record OpenGraphSnapshot(
        String title,
        String description,
        String image,
        String siteName
) {
    public static OpenGraphSnapshot empty() {
        return new OpenGraphSnapshot("", "", "", "");
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
