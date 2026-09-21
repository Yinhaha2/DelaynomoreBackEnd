package com.agentcrawler.agent.session;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话实体黑板：结构化锚点 + 动态 Prompt 注入，防止多轮检索注意力漂移。
 */
@Service
public class SessionBlackboardService {

    private static final Pattern EPISODE_PATTERN = Pattern.compile(
            "(?:第\\s*(\\d+)\\s*集|EP\\s*(\\d+)|Episode\\s*(\\d+))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*[:：分]\\s*(\\d{1,2})\\s*秒?"
    );

    private final SessionBlackboardStore store;

    public SessionBlackboardService(SessionBlackboardStore store) {
        this.store = store;
    }

    public AnimeSessionState get(String sessionId) {
        return store.get(sessionId);
    }

    public void clear(String sessionId) {
        store.clear(sessionId);
    }

    /** 用户发消息前：检测换题并尝试从文本提取锚点。 */
    public void onUserMessage(String sessionId, String message) {
        if (TopicSwitchDetector.isTopicSwitch(message)) {
            store.clear(sessionId);
            return;
        }
        AnimeSessionState state = store.getOrCreate(sessionId);
        EntityExtractor.extractWorkTitle(message).ifPresent(title -> {
            state.setWorkTitle(title);
            state.setLocked(true);
            state.setConfidence(Math.max(state.getConfidence(), 0.75));
        });
        EntityExtractor.extractCharacters(message).forEach(state::addCharacter);
        extractEpisodeProgress(message).ifPresent(state::setCurrentEpisode);
        store.save(sessionId, state);
    }

    /** 爬虫/识图结果返回后同步黑板。 */
    public void syncFromSearchResults(String sessionId, String keyword, List<String> titles) {
        AnimeSessionState state = store.getOrCreate(sessionId);
        if (keyword != null && !keyword.isBlank()) {
            EntityExtractor.extractWorkTitle(keyword).ifPresentOrElse(
                    title -> {
                        state.setWorkTitle(title);
                        state.setLocked(true);
                        state.setConfidence(Math.max(state.getConfidence(), 0.8));
                    },
                    () -> {
                        if (state.getWorkTitle() == null || state.getWorkTitle().isBlank()) {
                            state.setWorkTitle(keyword.trim());
                            state.setConfidence(Math.max(state.getConfidence(), 0.6));
                        }
                    }
            );
        }
        if (titles != null) {
            for (String title : titles) {
                if (title == null || title.isBlank()) {
                    continue;
                }
                EntityExtractor.extractWorkTitle(title).ifPresent(work -> {
                    state.setWorkTitle(work);
                    state.setLocked(true);
                    state.setConfidence(Math.max(state.getConfidence(), 0.85));
                });
            }
        }
        store.save(sessionId, state);
    }

    public void lockContext(
            String sessionId,
            String workTitle,
            List<String> characters,
            String currentEpisode,
            String searchScene,
            String visualFeatures
    ) {
        AnimeSessionState state = store.getOrCreate(sessionId);
        if (workTitle != null && !workTitle.isBlank()) {
            state.setWorkTitle(workTitle.trim());
            state.setLocked(true);
            state.setConfidence(Math.max(state.getConfidence(), 0.9));
        }
        if (characters != null) {
            characters.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .forEach(state::addCharacter);
        }
        if (currentEpisode != null && !currentEpisode.isBlank()) {
            state.setCurrentEpisode(currentEpisode.trim());
        }
        if (searchScene != null && !searchScene.isBlank()) {
            state.setSearchScene(searchScene.trim());
        }
        if (visualFeatures != null && !visualFeatures.isBlank()) {
            state.setVisualFeatures(visualFeatures.trim());
        }
        store.save(sessionId, state);
    }

    /** 组装强制上下文锚点块，注入到每轮用户消息头部。 */
    public String buildAnchorPrompt(String sessionId) {
        AnimeSessionState state = store.get(sessionId);
        if (state == null || !state.hasAnchor()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【系统强制上下文锚点 (System Anchor)】\n");
        if (state.getWorkTitle() != null && !state.getWorkTitle().isBlank()) {
            sb.append("- 当前锁定作品：").append(state.getWorkTitle()).append('\n');
        }
        if (!state.getCharacters().isEmpty()) {
            sb.append("- 核心背景实体：").append(String.join("、", state.getCharacters())).append('\n');
        }
        if (state.getCurrentEpisode() != null && !state.getCurrentEpisode().isBlank()) {
            sb.append("- 当前定位进度：").append(state.getCurrentEpisode()).append('\n');
        }
        if (state.getSearchScene() != null && !state.getSearchScene().isBlank()) {
            sb.append("- 检索场景：").append(state.getSearchScene()).append('\n');
        }
        if (state.getVisualFeatures() != null && !state.getVisualFeatures().isBlank()) {
            sb.append("- 已确认视觉特征：").append(state.getVisualFeatures()).append('\n');
        }
        sb.append("- 严格指令：除非用户明确声明「换一部」「换个话题」「不是这部」，否则后续所有剧情分析、")
                .append("角色识别、集数定位检索必须严格在已锁定作品的世界观内进行推导！")
                .append("绝对严禁引入其它无关动漫作品！\n");
        sb.append(buildDisambiguationHint(state));
        sb.append('\n');
        return sb.toString();
    }

    public String buildContextSummary(String sessionId) {
        AnimeSessionState state = store.get(sessionId);
        if (state == null || !state.hasAnchor()) {
            return "当前会话尚未锁定作品，请先识别图片或明确番剧名。";
        }
        List<String> parts = new ArrayList<>();
        if (state.getWorkTitle() != null) {
            parts.add("锁定作品=" + state.getWorkTitle());
        }
        if (!state.getCharacters().isEmpty()) {
            parts.add("角色=" + String.join("/", state.getCharacters()));
        }
        if (state.getCurrentEpisode() != null) {
            parts.add("进度=" + state.getCurrentEpisode());
        }
        if (state.getSearchScene() != null) {
            parts.add("场景=" + state.getSearchScene());
        }
        parts.add("locked=" + state.isLocked());
        parts.add("confidence=" + state.getConfidence());
        return String.join("；", parts);
    }

    private static String buildDisambiguationHint(AnimeSessionState state) {
        String blob = (
                nullToEmpty(state.getVisualFeatures())
                        + " "
                        + nullToEmpty(state.getSearchScene())
                        + " "
                        + String.join(" ", state.getCharacters())
        ).toLowerCase(Locale.ROOT);

        if (blob.contains("粉") && (blob.contains("吉他") || blob.contains("乐队") || blob.contains("band"))) {
            return "- 防撞脸提示：粉发吉他少女请通过发型（长直/及肩）、服装（运动服/羽丘女子制服）"
                    + "区分《孤独摇滚》后藤一里与 BanG Dream! It's MyGO!!!!! 千早爱音，禁止混淆。\n";
        }
        if (blob.contains("粉发") || blob.contains("粉色头发")) {
            return "- 防撞脸提示：粉发角色众多，必须结合已锁定作品与服装/发型/场景交叉验证，禁止跨作品张冠李戴。\n";
        }
        return "";
    }

    private static java.util.Optional<String> extractEpisodeProgress(String message) {
        if (message == null || message.isBlank()) {
            return java.util.Optional.empty();
        }
        Matcher ep = EPISODE_PATTERN.matcher(message);
        if (!ep.find()) {
            return java.util.Optional.empty();
        }
        String epNum = firstNonBlank(ep.group(1), ep.group(2), ep.group(3));
        Matcher time = TIME_PATTERN.matcher(message);
        if (time.find()) {
            return java.util.Optional.of("第" + epNum + "集 " + time.group(1) + "分" + time.group(2) + "秒");
        }
        return java.util.Optional.of("第" + epNum + "集");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "?";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
