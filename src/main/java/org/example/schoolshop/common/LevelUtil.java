package org.example.schoolshop.common;

public final class LevelUtil {

    private LevelUtil() {
    }

    public static int levelFromExp(int exp) {
        return Math.max(1, exp / 100 + 1);
    }

    public static int nextLevelExp(int level) {
        return level * 100;
    }

    public static String levelName(int level) {
        if (level >= 10) {
            return "校园传说";
        }
        if (level >= 7) {
            return "互助达人";
        }
        if (level >= 5) {
            return "校园达人";
        }
        if (level >= 3) {
            return "活跃同学";
        }
        return "萌新";
    }
}
