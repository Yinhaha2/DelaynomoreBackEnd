package org.example.schoolshop.service.impl;

import org.example.schoolshop.common.BizException;
import org.example.schoolshop.service.ContentSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ContentSecurityServiceImpl implements ContentSecurityService {

    private static final List<String> BLOCKED = List.of(
            "赌博", "色情", "暴力", "毒品", "诈骗", "反动", "傻逼", "操你", "去死"
    );

    @Override
    public void checkText(String text) {
        if (!StringUtils.hasText(text)) {
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
