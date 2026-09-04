package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.AgentHandler;
import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.agentcrawler.streaming.StreamEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LangChainStreamingAgent implements AgentHandler {
    private final ResourceCrawlerService crawlerService;
    private final ResourceCrawlTools crawlTools;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public LangChainStreamingAgent(
            ResourceCrawlerService crawlerService,
            ResourceCrawlTools crawlTools,
            AppProperties properties,
            ObjectMapper objectMapper
    ) {
        this.crawlerService = crawlerService;
        this.crawlTools = crawlTools;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void streamReply(String conversationId, String userMessage, String messageId, StreamEmitter emitter) {
        Optional<CrawlIntentParser.CrawlIntent> intent = CrawlIntentParser.parse(userMessage);
        if (intent.isPresent()) {
            if (streamCrawlResult(intent.get(), emitter)) {
                emitter.done(messageId, conversationId);
            }
            return;
        }

        if (hasOpenAiKey()) {
            streamWithLlm(conversationId, userMessage, messageId, emitter);
            return;
        }

        emitter.text("未能从消息中识别检索关键词。请使用类似：帮我找《番剧名》的播放资源，site=DM84");
        emitter.done(messageId, conversationId);
    }

    private void streamWithLlm(
            String conversationId,
            String userMessage,
            String messageId,
            StreamEmitter emitter
    ) {
        StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
                .apiKey(properties.openai().apiKey())
                .baseUrl(properties.openai().baseUrl())
                .modelName(properties.openai().model())
                .timeout(Duration.ofSeconds(60))
                .build();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatLanguageModel(model)
                .tools(crawlTools)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean failed = new AtomicBoolean(false);

        assistant.chat(conversationId, userMessage)
                .onNext(emitter::text)
                .onToolExecuted(toolExecution -> {
                    if ("crawlResources".equals(toolExecution.request().name())) {
                        emitStructuredResultFromJson(toolExecution.result(), emitter);
                    }
                })
                .onComplete(response -> latch.countDown())
                .onError(error -> {
                    errorRef.set(error);
                    failed.set(true);
                    latch.countDown();
                })
                .start();

        try {
            if (!latch.await(90, TimeUnit.SECONDS)) {
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
            emitStructuredResult(result, emitter);
            return true;
        } catch (AppException ex) {
            emitter.error(ex.getCode(), ex.getMessage());
            return false;
        } catch (Exception ex) {
            emitter.error(ErrorCode.CRAWL_FAILED, "爬虫执行失败: " + ex.getMessage());
            return false;
        }
    }

    private void emitStructuredResultFromJson(String rawResult, StreamEmitter emitter) {
        try {
            CrawlResourceResult result = objectMapper.readValue(rawResult, CrawlResourceResult.class);
            emitStructuredResult(result, emitter);
        } catch (Exception ex) {
            emitter.text(rawResult);
        }
    }

    private void emitStructuredResult(CrawlResourceResult result, StreamEmitter emitter) {
        emitter.text("插件: " + result.pluginName() + "\n");
        emitter.text("关键词: " + result.keyword() + "\n\n");

        if (!result.videos().isEmpty()) {
            emitter.text("视频资源（" + result.videos().size() + "）:\n");
            for (CrawlResourceResult.VideoResource video : result.videos()) {
                emitter.video(
                        video.url(),
                        video.title(),
                        detectVideoFormat(video.url()),
                        video.sourcePage(),
                        video.roadName()
                );
                emitter.text("- " + video.title() + " => " + video.url() + "\n");
            }
            emitter.text("\n");
        }

        if (!result.links().isEmpty()) {
            emitter.text("相关链接（" + result.links().size() + "）:\n");
            for (CrawlResourceResult.LinkResource link : result.links()) {
                emitter.link(link.url(), link.title(), link.description());
            }
            emitter.text("\n");
        }

        if (!result.images().isEmpty()) {
            emitter.text("图片资源（" + result.images().size() + "）:\n");
            for (CrawlResourceResult.ImageResource image : result.images()) {
                emitter.image(image.url(), image.alt());
            }
        }

        if (result.videos().isEmpty() && result.links().isEmpty() && result.images().isEmpty()) {
            emitter.text("未提取到可用资源，请更换关键词或站点后重试。");
        }
    }

    private boolean hasOpenAiKey() {
        String apiKey = properties.openai().apiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    private static String detectVideoFormat(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8")) {
            return "m3u8";
        }
        if (lower.contains(".mp4")) {
            return "mp4";
        }
        return "unknown";
    }

    interface StreamingAssistant {
        @SystemMessage("""
                你是 Agent Crawler 助手。用户想要检索视频/图片/链接资源时，
                必须调用 crawlResources 工具，传入关键词和目标站点。
                如果用户未指定站点，默认使用 DM84。
                回答时使用中文，简洁说明检索结果。
                """)
        dev.langchain4j.service.TokenStream chat(
                @MemoryId String conversationId,
                @UserMessage String userMessage
        );
    }
}
