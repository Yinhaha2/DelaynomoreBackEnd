package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.config.SchoolShopProperties;
import org.example.schoolshop.domain.UploadFile;
import org.example.schoolshop.integration.oss.OssStorageService;
import org.example.schoolshop.integration.wechat.WeChatClient;
import org.example.schoolshop.mapper.UploadFileMapper;
import org.example.schoolshop.service.ContentSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentSecurityServiceImpl implements ContentSecurityService {

    private static final List<String> BLOCKED = List.of(
            "赌博", "色情", "暴力", "毒品", "诈骗", "反动", "傻逼", "操你", "去死"
    );

    private final SchoolShopProperties properties;
    private final WeChatClient weChatClient;

    @Override
    public void checkText(String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if ("wechat".equalsIgnoreCase(properties.getSecurity().getContentCheckMode())
                && weChatClient.isConfigured()) {
            weChatClient.checkText(text);
            return;
        }
        String lower = text.toLowerCase();
        for (String word : BLOCKED) {
            if (lower.contains(word.toLowerCase())) {
                throw BizException.unprocessable("包含敏感词，请修改后重试");
            }
        }
    }
}
