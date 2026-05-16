package org.example.schoolshop.common;

import java.util.List;
import java.util.Map;

public final class CategoryConstants {

    private static final Map<Integer, String> POST_CATEGORY = Map.of(
            1, "校园生活",
            2, "吐槽",
            3, "表白墙",
            4, "失物招领"
    );

    private static final Map<String, List<String>> TASK_TAGS = Map.of(
            "pickup", List.of("代取"),
            "errand", List.of("跑腿"),
            "study", List.of("学习"),
            "other", List.of("其他")
    );

    private CategoryConstants() {
    }

    public static String postCategoryName(Integer categoryId) {
        return POST_CATEGORY.getOrDefault(categoryId, "校园");
    }

    public static List<String> defaultTaskTags(String category) {
        return TASK_TAGS.getOrDefault(category != null ? category : "other", List.of("其他"));
    }
}
