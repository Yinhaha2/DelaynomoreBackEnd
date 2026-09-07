package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.AgentHandler;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.agentcrawler.streaming.StreamEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

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

    public LangChainStreamingAgent(
            ResourceCrawlerService crawlerService,
            ObjectMapper objectMapper,
            ObjectProvider<AnimeAgent> animeAgentProvider
    ) {
        this.crawlerService = crawlerService;
        this.objectMapper = objectMapper;
        this.animeAgentProvider = animeAgentProvider;
    }

    @Override
    public void streamReply(String conversationId, String userMessage, String messageId, StreamEmitter emitter) {
        AnimeAgent animeAgent = animeAgentProvider.getIfAvailable();
        if (animeAgent != null) {
            streamWithAgent(animeAgent, conversationId, userMessage, messageId, emitter);
            return;
        }
        streamWithHeuristic(conversationId, userMessage, messageId, emitter);
    }

    private void streamWithAgent(
            AnimeAgent animeAgent,
            String conversationId,
            String userMessage,
            String messageId,
            StreamEmitter emitter
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean failed = new AtomicBoolean(false);

        animeAgent.chat(conversationId, userMessage)
                .onNext(emitter::text)
                .onToolExecuted(toolExecution -> {
                    if (CRAWL_TOOL_NAMES.contains(toolExecution.request().name())) {
                        CrawlResultEmitter.emitFromJson(toolExecution.result(), emitter, objectMapper);
                    }
                })
                .onComplete(response -> latch.countDown())
                .onError(error -> {
                    errorRef.set(error);
                    failed.set(true);
                    latch.countDown();
                })
                .start();

        awaitAndFinish(latch, errorRef, failed, messageId, conversationId, emitter);
    }

    private void streamWithHeuristic(
            String conversationId,
            String userMessage,
            String messageId,
            StreamEmitter emitter
    ) {
        Optional<CrawlIntentParser.CrawlIntent> intent = CrawlIntentParser.parse(userMessage);
        if (intent.isPresent()) {
            if (streamCrawlResult(intent.get(), emitter)) {
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

    private boolean streamCrawlResult(CrawlIntentParser.CrawlIntent intent, StreamEmitter emitter) {
        emitter.text("正在站点 " + intent.site() + " 检索「" + intent.keyword() + "」...\n\n");
        try {
            CrawlResourceResult result = crawlerService.crawl(intent.keyword(), intent.site());
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
