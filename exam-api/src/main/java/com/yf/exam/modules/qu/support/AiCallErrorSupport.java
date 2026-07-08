package com.yf.exam.modules.qu.support;

import com.yf.exam.core.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 调用异常识别与用户可读提示
 */
public final class AiCallErrorSupport {

    private static final Pattern API_ERROR_MESSAGE = Pattern.compile(
            "\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern EXCEPTION_PREFIX = Pattern.compile(
            "^[\\w.$]+(?:Exception|Error):\\s*");

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
            return "AI 调用失败";
        }
        if (isTransientError(error)) {
            return "AI 服务繁忙，请稍后重试";
        }
        String raw = collectMessage(error);
        String apiMessage = extractApiErrorMessage(raw);
        String text = StringUtils.defaultIfBlank(apiMessage, raw);
        text = stripExceptionPrefix(text);
        String localized = localizeMessage(text);
        if (StringUtils.isNotBlank(localized)) {
            return trimLength(localized);
        }
        if (StringUtils.isBlank(text)) {
            return "AI 调用失败";
        }
        if (looksLikeEnglishApiError(text)) {
            return trimLength("AI 接口返回错误：" + text);
        }
        return trimLength(text);
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

    private static String extractApiErrorMessage(String message) {
        if (StringUtils.isBlank(message)) {
            return null;
        }
        Matcher matcher = API_ERROR_MESSAGE.matcher(message);
        String last = null;
        while (matcher.find()) {
            last = unescapeJson(matcher.group(1));
        }
        return last;
    }

    private static String stripExceptionPrefix(String message) {
        if (StringUtils.isBlank(message)) {
            return message;
        }
        int jsonStart = message.indexOf("{\"error\"");
        if (jsonStart > 0) {
            return message.substring(jsonStart).trim();
        }
        return EXCEPTION_PREFIX.matcher(message).replaceFirst("").trim();
    }

    private static String localizeMessage(String message) {
        if (StringUtils.isBlank(message)) {
            return null;
        }
        String htmlStatus = extractHtmlHttpStatus(message);
        if (StringUtils.isNotBlank(htmlStatus)) {
            return localizeHttpStatus(htmlStatus, message);
        }
        String lower = message.toLowerCase();

        if (lower.contains("prompt must contain the word 'json'")
                || (lower.contains("response_format") && lower.contains("json_object"))) {
            return "使用 JSON 输出格式时，提示词中需包含 json 字样";
        }
        if (lower.contains("incorrect api key")
                || lower.contains("invalid api key")
                || lower.contains("invalid authentication")
                || lower.contains("authentication failed")
                || lower.contains("unauthorized")
                || lower.contains("invalid_api_key")
                || lower.contains("api key not valid")) {
            return "API Key 无效或已过期，请检查配置";
        }
        if (lower.contains("exceeded your current quota")
                || lower.contains("insufficient_quota")
                || lower.contains("billing")
                || lower.contains("余额不足")
                || lower.contains("quota")) {
            return "API 调用额度不足或账户欠费，请检查服务商账户";
        }
        if (lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("ratelimit")
                || lower.contains(" 429")) {
            return "请求过于频繁，请稍后重试";
        }
        if (lower.contains("model") && (lower.contains("not found")
                || lower.contains("does not exist")
                || lower.contains("not exist")
                || lower.contains("unknown model"))) {
            return "模型不存在或当前账户无权访问，请检查模型名称";
        }
        if (lower.contains("connect timed out")
                || lower.contains("read timed out")
                || lower.contains("timeout")
                || lower.contains("timed out")) {
            return "连接 AI 服务超时，请检查网络或增大超时时间";
        }
        if (lower.contains("connection refused")
                || lower.contains("failed to connect")
                || lower.contains("unknown host")
                || lower.contains("no route to host")
                || lower.contains("network is unreachable")) {
            return "无法连接 AI 服务，请检查 API 地址与网络";
        }
        if (lower.contains("ssl")
                || lower.contains("certificate")
                || lower.contains("handshake")) {
            return "连接 AI 服务时 SSL 证书校验失败，请检查 API 地址";
        }
        if (lower.contains("context length")
                || lower.contains("maximum context")
                || lower.contains("token limit")
                || lower.contains("max_tokens")) {
            return "输入内容过长，超出模型上下文限制";
        }
        if (lower.contains("invalid_request_error")
                || lower.contains("invalid request")
                || lower.contains("bad request")
                || lower.contains(" 400")) {
            return "AI 接口请求参数无效，请检查配置";
        }
        if (lower.contains("forbidden")
                || lower.contains(" 403")) {
            return "无权访问该 AI 接口，请检查 API Key 与权限";
        }
        if (lower.contains("not allowed")
                || lower.contains("method not allowed")
                || lower.contains(" 405")) {
            return "AI 接口地址不正确或网关不允许该请求方式（405），请检查 API 地址是否填写为服务商提供的接口根地址";
        }
        if (lower.contains("not found")
                || lower.contains(" 404")) {
            return "AI 接口地址不存在，请检查 API 地址配置";
        }
        if (lower.contains("internal server error")
                || lower.contains("server error")
                || lower.contains(" 500")
                || lower.contains(" 502")
                || lower.contains(" 503")
                || lower.contains(" 504")) {
            return "AI 服务暂时不可用，请稍后重试";
        }
        if (lower.contains("service is too busy")
                || lower.contains("service_unavailable")
                || lower.contains("overloaded")) {
            return "AI 服务繁忙，请稍后重试";
        }
        if (containsChinese(message)) {
            return message;
        }
        return null;
    }

    private static boolean looksLikeEnglishApiError(String message) {
        if (StringUtils.isBlank(message)) {
            return false;
        }
        if (StringUtils.isNotBlank(extractHtmlHttpStatus(message))) {
            return true;
        }
        return !containsChinese(message)
                && (message.contains("error")
                || message.contains("invalid")
                || message.contains("failed")
                || message.contains("exception"));
    }

    private static String extractHtmlHttpStatus(String message) {
        if (StringUtils.isBlank(message) || !message.toLowerCase().contains("<html")) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)(\\d{3})\\s+([A-Za-z ]+)").matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + " " + matcher.group(2).trim();
        }
        return "HTML";
    }

    private static String localizeHttpStatus(String status, String rawMessage) {
        String lower = status.toLowerCase();
        if (lower.contains("405")) {
            return "AI 接口地址不正确或网关不允许该请求方式（405），请检查 API 地址是否填写为服务商提供的接口根地址，例如 https://api.deepseek.com";
        }
        if (lower.contains("404")) {
            return "AI 接口地址不存在（404），请检查 API 地址配置";
        }
        if (lower.contains("401") || lower.contains("403")) {
            return "无权访问 AI 接口（" + status.trim() + "），请检查 API Key 与地址";
        }
        if (lower.contains("502") || lower.contains("503") || lower.contains("504")) {
            return "AI 服务网关异常（" + status.trim() + "），请稍后重试或检查 API 地址";
        }
        if (StringUtils.containsIgnoreCase(rawMessage, "nginx")) {
            return "请求被 Web 网关拒绝（" + status.trim() + "），API 地址可能填错了，请填写 AI 服务商的接口地址而非网站首页";
        }
        return "AI 接口返回异常（" + status.trim() + "），请检查 API 地址与网络";
    }

    private static boolean containsChinese(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private static String trimLength(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= 240) {
            return message;
        }
        return message.substring(0, 240) + "...";
    }
}
