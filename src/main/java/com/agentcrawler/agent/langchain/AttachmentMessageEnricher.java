package com.agentcrawler.agent.langchain;

import com.agentcrawler.model.ChatAttachment;
import com.agentcrawler.vision.ImageUploadService;

import java.util.ArrayList;
import java.util.List;

public final class AttachmentMessageEnricher {

    private AttachmentMessageEnricher() {
    }

    public static String enrich(
            String userMessage,
            List<ChatAttachment> attachments,
            ImageUploadService imageUploadService
    ) {
        if (attachments == null || attachments.isEmpty()) {
            return userMessage;
        }

        List<String> imageUrls = resolveImageUrls(attachments, imageUploadService);
        if (imageUrls.isEmpty()) {
            return userMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【用户附带图片】\n");
        for (int i = 0; i < imageUrls.size(); i++) {
            sb.append("- 图片").append(i + 1).append(": ").append(imageUrls.get(i)).append('\n');
        }
        sb.append("请先调用 analyzeAnimeImage 工具分析以上图片（一次一张），再检索或回答。\n");
        sb.append("参数 imageUrl 只传上述 URL，禁止传 base64。\n\n");
        sb.append("【用户当前提问】\n");
        sb.append(userMessage);
        return sb.toString();
    }

    private static List<String> resolveImageUrls(
            List<ChatAttachment> attachments,
            ImageUploadService imageUploadService
    ) {
        List<String> urls = new ArrayList<>();
        for (ChatAttachment attachment : attachments) {
            if (attachment == null || !attachment.isImage()) {
                continue;
            }
            if (attachment.imageUrl() != null && !attachment.imageUrl().isBlank()) {
                urls.add(attachment.imageUrl().trim());
                continue;
            }
            if (attachment.imageId() != null && !attachment.imageId().isBlank()) {
                urls.add(imageUploadService.buildPublicUrl(attachment.imageId().trim()));
            }
        }
        return urls;
    }
}
