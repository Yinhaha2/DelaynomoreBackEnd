package com.agentcrawler.link;

import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LinkInspectorService {

    private final LinkHttpClient httpClient;
    private final SessionBlackboardService blackboardService;
    private final AppProperties properties;

    public LinkInspectorService(
            LinkHttpClient httpClient,
            SessionBlackboardService blackboardService,
            AppProperties properties
    ) {
        this.httpClient = httpClient;
        this.blackboardService = blackboardService;
        this.properties = properties;
    }

    public List<LinkInspectionResult> inspectMessage(String sessionId, String message) {
        List<ExtractedLink> extracted = LinkExtractor.extract(message);
        int limit = Math.max(1, properties.link().maxLinksPerMessage());
        List<LinkInspectionResult> results = new ArrayList<>();
        for (ExtractedLink link : extracted) {
            if (results.size() >= limit) {
                break;
            }
            LinkInspectionResult result = inspect(link.raw());
            autoLock(sessionId, result);
            results.add(result);
        }
        return results;
    }

    public LinkInspectionResult inspect(String url) {
        ExtractedLink extracted = new ExtractedLink(url, UrlClassifier.classify(url));
        return inspect(extracted);
    }

    public LinkInspectionResult inspectAndLock(String sessionId, String url) {
        LinkInspectionResult result = inspect(url);
        autoLock(sessionId, result);
        return result;
    }

    public LinkInspectionResult inspect(ExtractedLink extracted) {
        LinkInspectionResult result = new LinkInspectionResult();
        result.setUrl(extracted.raw());
        result.setFinalUrl(extracted.raw());
        result.setKind(extracted.kind());

        if (extracted.kind() == LinkKind.MAGNET) {
            fillMagnet(result, extracted.raw());
            return result;
        }
        if (shouldBlock(extracted.raw())) {
            result.setKind(LinkKind.BLOCKED);
            result.setAlive(false);
            result.setError("拒绝探测内网或非 http(s) 地址");
            return result;
        }
        if (extracted.kind() == LinkKind.STREAM) {
            fillStream(result, extracted.raw());
            return result;
        }

        inspectHttp(result, extracted.raw());
        return result;
    }

    private void inspectHttp(LinkInspectionResult result, String url) {
        LinkHttpClient.HtmlFetch fetch = httpClient.fetchHtml(url);
        result.setHttpStatus(fetch.status());
        result.setFinalUrl(fetch.finalUrl() == null || fetch.finalUrl().isBlank() ? url : fetch.finalUrl());
        result.setContentType(fetch.contentType());

        if (fetch.error() != null && !fetch.error().isBlank() && (fetch.html() == null || fetch.html().isBlank())) {
            result.setAlive(false);
            result.setError(fetch.error());
            return;
        }
        if (fetch.status() == 404 || fetch.status() == 410 || fetch.status() == 451) {
            result.setKind(LinkKind.DEAD);
            result.setAlive(false);
            result.setError("链接不可用 HTTP " + fetch.status());
            return;
        }

        String effectiveUrl = result.getFinalUrl();
        result.setKind(UrlClassifier.classify(effectiveUrl));
        VideoSiteParser.VideoSiteHint hint = VideoSiteParser.parse(effectiveUrl);
        if (hint.hasMediaId() || UrlClassifier.isVideoHost(UrlClassifier.hostOf(effectiveUrl))) {
            result.setKind(LinkKind.VIDEO_SITE);
            result.setSiteHint(hint.site());
            result.setMediaId(hint.mediaId());
        }

        String contentType = fetch.contentType() == null ? "" : fetch.contentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("video/") || contentType.contains("mpegurl") || result.getKind() == LinkKind.STREAM) {
            result.setKind(LinkKind.STREAM);
            result.setTitle(fileName(effectiveUrl));
            result.setConfidence(0.8);
            result.setAlive(true);
            return;
        }

        OpenGraphSnapshot og = OpenGraphParser.parse(fetch.html());
        result.setTitle(og.title());
        result.setDescription(og.description());
        result.setImage(og.image());
        WorkTitleNormalizer.NormalizedTitle normalized = WorkTitleNormalizer.normalize(og.title());
        if (normalized.hasWorkTitle()) {
            result.setWorkTitle(normalized.workTitle());
            result.setEpisodeHint(normalized.episodeHint());
            result.setConfidence(normalized.confidence());
        }
        result.setAlive(fetch.successful() || result.getTitle() != null);
    }

    private static void fillMagnet(LinkInspectionResult result, String magnet) {
        MagnetLinkParser.MagnetInfo info = MagnetLinkParser.parse(magnet);
        result.setKind(LinkKind.MAGNET);
        result.setAlive(info.hasHash());
        result.setInfoHash(info.infoHash());
        result.setDisplayName(info.displayName());
        result.setTitle(info.displayName());
        WorkTitleNormalizer.NormalizedTitle normalized = WorkTitleNormalizer.normalize(info.displayName());
        if (normalized.hasWorkTitle()) {
            result.setWorkTitle(normalized.workTitle());
            result.setEpisodeHint(normalized.episodeHint());
            result.setConfidence(Math.max(0.6, normalized.confidence() - 0.1));
        } else if (!info.displayName().isBlank()) {
            result.setWorkTitle(info.displayName());
            result.setConfidence(0.55);
        }
    }

    private static void fillStream(LinkInspectionResult result, String url) {
        result.setKind(LinkKind.STREAM);
        result.setTitle(fileName(url));
        result.setConfidence(0.75);
        result.setAlive(true);
    }

    private boolean shouldBlock(String url) {
        return !properties.link().allowPrivateHosts() && LinkSafety.isBlocked(url);
    }

    private void autoLock(String sessionId, LinkInspectionResult result) {
        if (sessionId == null || sessionId.isBlank() || !result.hasWorkTitle()) {
            return;
        }
        if (result.getConfidence() < properties.link().minConfidenceToLock()) {
            return;
        }
        String scene = result.getKind() == null ? "链接解析" : "链接解析:" + result.getKind().name();
        blackboardService.lockContext(
                sessionId,
                result.getWorkTitle(),
                List.of(),
                result.getEpisodeHint(),
                scene,
                result.getTitle()
        );
    }

    private static String fileName(String url) {
        String path = url.split("[?#]", 2)[0];
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }
}
