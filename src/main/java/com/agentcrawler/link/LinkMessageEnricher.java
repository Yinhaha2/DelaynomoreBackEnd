package com.agentcrawler.link;

import java.util.List;

public final class LinkMessageEnricher {

    private LinkMessageEnricher() {
    }

    public static String enrich(String userMessage, List<LinkInspectionResult> inspections) {
        if (inspections == null || inspections.isEmpty()) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【系统前置链接解析 (Deterministic Link Inspector)】\n");
        sb.append("以下链接已由后端确定性提取并嗅探，无需再从原文里猜 URL。\n");
        for (int i = 0; i < inspections.size(); i++) {
            LinkInspectionResult item = inspections.get(i);
            sb.append("- 链接").append(i + 1).append("：").append(nullToEmpty(item.getUrl())).append('\n');
            if (item.getFinalUrl() != null && !item.getFinalUrl().equals(item.getUrl())) {
                sb.append("  还原后：").append(item.getFinalUrl()).append('\n');
            }
            sb.append("  类型：").append(item.getKind()).append('\n');
            sb.append("  可用：").append(item.isAlive()).append('\n');
            appendIfPresent(sb, "  页面标题", item.getTitle());
            appendIfPresent(sb, "  锁定作品", item.getWorkTitle());
            appendIfPresent(sb, "  集数线索", item.getEpisodeHint());
            appendIfPresent(sb, "  站点", item.getSiteHint());
            appendIfPresent(sb, "  媒体ID", item.getMediaId());
            appendIfPresent(sb, "  InfoHash", item.getInfoHash());
            appendIfPresent(sb, "  摘要", truncate(item.getDescription(), 120));
            if (item.getError() != null && !item.getError().isBlank()) {
                sb.append("  错误：").append(item.getError()).append('\n');
            }
        }
        sb.append("若用户要找在线资源，优先用已锁定作品名调用 searchResources；")
                .append("仅当还需要补充解析某个未覆盖 URL 时才调用 inspectLink。\n\n");
        sb.append("【用户当前提问】\n");
        sb.append(userMessage);
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
