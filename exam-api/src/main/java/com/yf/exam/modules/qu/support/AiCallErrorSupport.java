package com.yf.exam.modules.qu.support;

import com.yf.exam.core.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

/**
 * AI 调用异常识别与用户可读提示
 */
public final class AiCallErrorSupport {

    private AiCallErrorSupport() {
    }

    public static boolean isTransientError(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = collectMessage(error).toLowerCase();
        return message.contains("service is too busy")
                || message.contains("service_unavailable")
                || message.contains("too many requests")
                || message.contains("rate limit")
                || message.contains("ratelimit")
                || message.contains("overloaded")
                || message.contains("temporarily unavailable")
                || message.contains(" 429")
                || message.contains("\"code\":\"service_unavailable")
                || message.contains("ai服务繁忙");
    }

    public static String toUserMessage(Throwable error) {
        if (error == null) {
            return "AI调用失败";
        }
        if (isTransientError(error)) {
            return "AI服务繁忙，请稍后重试该批次";
        }
        String message = collectMessage(error);
        if (StringUtils.isBlank(message)) {
            return "AI调用失败";
        }
        if (message.length() > 180) {
            return message.substring(0, 180) + "...";
        }
        return message;
    }

    public static void sleepBeforeRetry(int attempt) {
        long delayMs = Math.min(15000L, 2000L * Math.max(1, attempt));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String collectMessage(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.isBlank(message) && current instanceof ServiceException) {
                message = ((ServiceException) current).getMsg();
            }
            if (StringUtils.isNotBlank(message)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(message.trim());
            }
            current = current.getCause();
        }
        if (sb.length() == 0 && error != null) {
            sb.append(error.getClass().getSimpleName());
        }
        return sb.toString();
    }
}
