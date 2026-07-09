package com.yf.exam.modules.qu.config;

/**
 * 异步 AI 任务线程中的当前用户上下文（用于读取该用户的 AI 配置）。
 */
public final class AiUserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private AiUserContext() {
    }

    public static void setUserId(String userId) {
        if (userId == null) {
            USER_ID.remove();
            return;
        }
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
