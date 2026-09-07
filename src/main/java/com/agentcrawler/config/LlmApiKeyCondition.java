package com.agentcrawler.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 以 Environment 实际取值判断，避免 {@code @ConditionalOnExpression} 占位符只展开一层、
 * 以及 {@code @ConditionalOnBean} 跨配置类时因扫描顺序漏装 Agent。
 */
public class LlmApiKeyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return hasText(env.getProperty("agent.llm.api-key"))
                || hasText(env.getProperty("DEEPSEEK_API_KEY"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
