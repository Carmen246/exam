package com.yf.exam.ability.shiro;

import org.apache.shiro.web.util.WebUtils;
import org.springframework.util.AntPathMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shiro 匿名访问路径（与 ShiroConfig 保持一致）
 */
public final class AnonPathUtils {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> ANON_PATTERNS = Arrays.asList(
            "/exam/api/sys/user/login",
            "/exam/api/sys/user/reg",
            "/exam/api/sys/user/quick-reg",
            "/exam/api/sys/config/detail",
            "/upload/file/**",
            "/",
            "/v2/**",
            "/doc.html",
            "/**/*.js",
            "/**/*.css",
            "/**/*.html",
            "/**/*.svg",
            "/**/*.pdf",
            "/**/*.jpg",
            "/**/*.png",
            "/**/*.ico",
            "/**/*.ttf",
            "/**/*.woff",
            "/**/*.woff2",
            "/druid/**",
            "/swagger-ui.html",
            "/swagger**/**",
            "/webjars/**"
    );

    private AnonPathUtils() {
    }

    public static Map<String, String> anonFilterChainMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pattern : ANON_PATTERNS) {
            map.put(pattern, "anon");
        }
        return map;
    }

    public static boolean isAnonPath(HttpServletRequest request) {
        String path = resolvePath(request);
        for (String pattern : ANON_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public static String resolvePath(HttpServletRequest request) {
        String path = WebUtils.getPathWithinApplication(request);
        if (path == null || path.isEmpty()) {
            path = request.getServletPath();
        }
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path == null ? "" : path;
    }
}
