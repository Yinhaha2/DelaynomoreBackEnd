package com.agentcrawler.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

class CosCacheCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return present(env, "COS_SECRET_ID")
                && present(env, "COS_SECRET_KEY")
                && present(env, "COS_BUCKET");
    }

    private static boolean present(Environment env, String key) {
        String value = env.getProperty(key);
        return value != null && !value.isBlank();
    }
}
