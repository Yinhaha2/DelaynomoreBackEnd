package org.example.schoolshop.common;

public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw BizException.unauthorized("请先登录");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
