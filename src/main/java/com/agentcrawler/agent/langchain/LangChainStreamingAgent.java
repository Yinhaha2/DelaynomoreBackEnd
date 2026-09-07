package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.AgentHandler;
import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.agentcrawler.link.LinkInspectionResult;
import com.agentcrawler.link.LinkInspectorService;
import com.agentcrawler.link.LinkMessageEnricher;
import com.agentcrawler.model.ChatAttachment;
import com.agentcrawler.streaming.StreamEmitter;
import com.agentcrawler.vision.ImageUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LangChainStreamingAgent implements AgentHandler {
    private static final Set<String> CRAWL_TOOL_NAMES = Set.of("searchResources", "crawlResources");
    private static final Set<String> VISION_TOOL_NAMES = Set.of("analyzeAnimeImage");
    private static final Set<String> LINK_TOOL_NAMES = Set.of("inspectLink");

    private final ResourceCrawlerService crawlerService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AnimeAgent> animeAgentProvider;
    private final SessionBlackboardService blackboardService;
    private final ImageUploadService imageUploadService;
    private final LinkInspectorService linkInspectorService;

    public LangChainStreamingAgent(
            ResourceCrawlerService crawlerService,
            ObjectMapper objectMapper,
            ObjectProvider<AnimeAgent> animeAgentProvider,
            SessionBlackboardService blackboardService,
            ImageUploadService imageUploadService,
            LinkInspectorService linkInspectorService
    ) {
        this.crawlerService = crawlerService;
        this.objectMapper = objectMapper;
        this.animeAgentProvider = animeAgentProvider;
        this.blackboardService = blackboardService;
        this.imageUploadService = imageUploadService;
        this.linkInspectorService = linkInspectorService;
    }

    @Override
    public void streamReply(
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            String messageId,
            StreamEmitter emitter
    ) {
        AnimeAgent animeAgent = animeAgentProvider.getIfAvailable();
        if (animeAgent != null) {
            streamWithAgent(animeAgent, conversationId, userMessage, attachments, messageId, emitter);
            return;
        }
        streamWithHeuristic(conversationId, userMessage, messageId, emitter);
    }

    private void streamWithAgent(
            AnimeAgent animeAgent,
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            String messageId,
            StreamEmitter emitter
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean failed = new AtomicBoolean(false);

        blackboardService.onUserMessage(conversationId, userMessage);
        List<LinkInspectionResult> inspections = linkInspectorService.inspectMessage(conversationId, userMessage);
        String enrichedMessage = AttachmentMessageEnricher.enrich(userMessage, attachments, imageUploadService);
        enrichedMessage = LinkMessageEnricher.enrich(enrichedMessage, inspections);
        String anchoredMessage = prependAnchor(conversationId, enrichedMessage);

        SessionContextHolder.set(conversationId);
        try {
            animeAgent.chat(conversationId, anchoredMessage)
                    .onNext(emitter::text)
                    .onToolExecuted(toolExecution -> {
                        String toolName = toolExecution.request().name();
                        if (CRAWL_TOOL_NAMES.contains(toolName)) {
                            CrawlResultEmitter.emitFromJson(toolExecution.result(), emitter, objectMapper);
                            syncBlackboardFromCrawl(conversationId, toolExecution.result());
                        } else if (VISION_TOOL_NAMES.contains(toolName)) {
                            emitter.text("\n[视觉识别完成]\n");
                        } else if (LINK_TOOL_NAMES.contains(toolName)) {
                            emitter.text("\n[链接解析完成]\n");
                        }
                    })
                    .onComplete(response -> latch.countDown())
                    .onError(error -> {
                        errorRef.set(error);
                        failed.set(true);
                        latch.countDown();
                    })
                    .start();
        } finally {
            SessionContextHolder.clear();
        }

        awaitAndFinish(latch, errorRef, failed, messageId, conversationId, emitter);
    }

    private String prependAnchor(String conversationId, String userMessage) {
        String anchor = blackboardService.buildAnchorPrompt(conversationId);
        if (anchor.isBlank()) {
            return userMessage;
        }
        return anchor + "\n【用户当前提问】\n" + userMessage;
    }

    private void syncBlackboardFromCrawl(String conversationId, String rawResult) {
        try {
            CrawlResourceResult result = objectMapper.readValue(rawResult, CrawlResourceResult.class);
            List<String> titles = new ArrayList<>();
            result.videos().forEach(v -> titles.add(v.title()));
            result.links().forEach(l -> titles.add(l.title()));
            blackboardService.syncFromSearchResults(conversationId, result.keyword(), titles);
        } catch (Exception ignored) {
            // 黑板同步失败不影响主流程
        }
    }

    private void streamWithHeuristic(
            String conversationId,
            String userMessage,
            String messageId,
            StreamEmitter emitter
    ) {
        blackboardService.onUserMessage(conversationId, userMessage);
        Optional<CrawlIntentParser.CrawlIntent> intent = CrawlIntentParser.parse(userMessage);
        if (intent.isPresent()) {
            if (streamCrawlResult(conversationId, intent.get(), emitter)) {
                emitter.done(messageId, conversationId);
            }
            return;
        }
        emitter.text("""
                当前未配置 LLM API Key，已启用启发式检索模式。
                请使用类似：帮我找《番剧名》的播放资源
                或配置环境变量 DEEPSEEK_API_KEY 以启用多轮对话 Agent。
                """);
        emitter.done(messageId, conversationId);
    }

    private void awaitAndFinish(
            CountDownLatch latch,
            AtomicReference<Throwable> errorRef,
            AtomicBoolean failed,
            String messageId,
            String conversationId,
            StreamEmitter emitter
    ) {
        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                emitter.error(ErrorCode.AGENT_TIMEOUT, "Agent 处理超时，请稍后重试");
                return;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.error(ErrorCode.AGENT_ERROR, "Agent 处理被中断");
            return;
        }

        if (failed.get() && errorRef.get() != null) {
            emitter.error(ErrorCode.AGENT_ERROR, "Agent 内部错误: " + errorRef.get().getMessage());
            return;
        }
        emitter.done(messageId, conversationId);
    }

    private boolean streamCrawlResult(
            String conversationId,
            CrawlIntentParser.CrawlIntent intent,
            StreamEmitter emitter
    ) {
        emitter.text("正在站点 " + intent.site() + " 检索「" + intent.keyword() + "」...\n\n");
        try {
            CrawlResourceResult result = crawlerService.crawl(intent.keyword(), intent.site());
            List<String> titles = new ArrayList<>();
            result.videos().forEach(v -> titles.add(v.title()));
            result.links().forEach(l -> titles.add(l.title()));
            blackboardService.syncFromSearchResults(conversationId, result.keyword(), titles);
            CrawlResultEmitter.emit(result, emitter);
            return true;
        } catch (AppException ex) {
            emitter.error(ex.getCode(), ex.getMessage());
            return false;
        } catch (Exception ex) {
            emitter.error(ErrorCode.CRAWL_FAILED, "爬虫执行失败: " + ex.getMessage());
            return false;
        }
    }
}
