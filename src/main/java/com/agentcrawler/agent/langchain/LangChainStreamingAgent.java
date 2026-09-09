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
import com.agentcrawler.service.ConversationTitleGenerator;
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
            boolean needTitle,
            String messageId,
            StreamEmitter emitter
    ) {
        AnimeAgent animeAgent = animeAgentProvider.getIfAvailable();
        if (animeAgent != null) {
            streamWithAgent(animeAgent, conversationId, userMessage, attachments, needTitle, messageId, emitter);
            return;
        }
        streamWithHeuristic(conversationId, userMessage, attachments, needTitle, messageId, emitter);
    }

    private void streamWithAgent(
            AnimeAgent animeAgent,
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            boolean needTitle,
            String messageId,
            StreamEmitter emitter
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean failed = new AtomicBoolean(false);
        AgentTokenGate tokenGate = new AgentTokenGate();

        blackboardService.onUserMessage(conversationId, userMessage);
        List<LinkInspectionResult> inspections = linkInspectorService.inspectMessage(conversationId, userMessage);
        String enrichedMessage = AttachmentMessageEnricher.enrich(userMessage, attachments, imageUploadService);
        enrichedMessage = LinkMessageEnricher.enrich(enrichedMessage, inspections);
        String anchoredMessage = prependAnchor(conversationId, enrichedMessage);

        SessionContextHolder.set(conversationId);
        try {
            animeAgent.chat(conversationId, anchoredMessage)
                    .onNext(tokenGate::append)
                    .onToolExecuted(toolExecution -> {
                        tokenGate.discardIntermediate();
                        String toolName = toolExecution.request().name();
                        if (CRAWL_TOOL_NAMES.contains(toolName)) {
                            emitBufferedCrawlResult(conversationId, toolExecution.result(), emitter);
                        }
                    })
                    .onComplete(response -> {
                        String finalText = tokenGate.takeFinalText();
                        if (!finalText.isBlank()) {
                            emitter.text(finalText);
                        }
                        latch.countDown();
                    })
                    .onError(error -> {
                        errorRef.set(error);
                        failed.set(true);
                        latch.countDown();
                    })
                    .start();
            awaitAndFinish(latch, errorRef, failed, messageId, conversationId, userMessage, attachments, needTitle, emitter);
        } finally {
            SessionContextHolder.clear();
            CrawlResultBuffer.clear(conversationId);
        }
    }

    private String prependAnchor(String conversationId, String userMessage) {
        String anchor = blackboardService.buildAnchorPrompt(conversationId);
        if (anchor.isBlank()) {
            return userMessage;
        }
        return anchor + "\n【用户当前提问】\n" + userMessage;
    }

    private void emitBufferedCrawlResult(String conversationId, String rawResult, StreamEmitter emitter) {
        CrawlResourceResult buffered = CrawlResultBuffer.poll(conversationId);
        if (buffered != null) {
            CrawlResultEmitter.emit(buffered, emitter);
            syncBlackboardFromResult(conversationId, buffered);
            return;
        }
        CrawlResultEmitter.emitFromJson(rawResult, emitter, objectMapper);
        syncBlackboardFromCrawlJson(conversationId, rawResult);
    }

    private void syncBlackboardFromCrawlJson(String conversationId, String rawResult) {
        try {
            CrawlResourceResult result = objectMapper.readValue(rawResult, CrawlResourceResult.class);
            syncBlackboardFromResult(conversationId, result);
        } catch (Exception ignored) {
            // 摘要 JSON 无法还原全量结果时跳过黑板同步
        }
    }

    private void syncBlackboardFromResult(String conversationId, CrawlResourceResult result) {
        if (result == null) {
            return;
        }
        List<String> titles = new ArrayList<>();
        if (result.videos() != null) {
            result.videos().forEach(v -> titles.add(v.title()));
        }
        if (result.links() != null) {
            result.links().forEach(l -> titles.add(l.title()));
        }
        blackboardService.syncFromSearchResults(conversationId, result.keyword(), titles);
    }

    private void streamWithHeuristic(
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            boolean needTitle,
            String messageId,
            StreamEmitter emitter
    ) {
        blackboardService.onUserMessage(conversationId, userMessage);
        Optional<CrawlIntentParser.CrawlIntent> intent = CrawlIntentParser.parse(userMessage);
        if (intent.isPresent()) {
            if (streamCrawlResult(conversationId, intent.get(), emitter)) {
                finishDone(conversationId, userMessage, attachments, needTitle, messageId, emitter);
            }
            return;
        }
        emitter.text("""
                当前未配置 LLM API Key，已启用启发式检索模式。
                请使用类似：帮我找《番剧名》的播放资源
                或在项目根目录 .env 中配置 DEEPSEEK_API_KEY 后重启后端。
                """);
        finishDone(conversationId, userMessage, attachments, needTitle, messageId, emitter);
    }

    private void awaitAndFinish(
            CountDownLatch latch,
            AtomicReference<Throwable> errorRef,
            AtomicBoolean failed,
            String messageId,
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            boolean needTitle,
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
        finishDone(conversationId, userMessage, attachments, needTitle, messageId, emitter);
    }

    private void finishDone(
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            boolean needTitle,
            String messageId,
            StreamEmitter emitter
    ) {
        String title = null;
        if (needTitle) {
            String locked = null;
            var state = blackboardService.get(conversationId);
            if (state != null && state.getWorkTitle() != null && !state.getWorkTitle().isBlank()) {
                locked = state.getWorkTitle();
            }
            boolean hasAttachments = attachments != null && !attachments.isEmpty();
            title = ConversationTitleGenerator.generate(userMessage, hasAttachments, locked);
        }
        emitter.done(messageId, conversationId, title);
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
            if (!result.hasResources()) {
                CrawlResultEmitter.emit(result, emitter, true);
                return true;
            }
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
