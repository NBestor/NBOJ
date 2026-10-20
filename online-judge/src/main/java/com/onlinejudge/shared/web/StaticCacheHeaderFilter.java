package com.onlinejudge.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class StaticCacheHeaderFilter extends OncePerRequestFilter {

    private static final long ONE_YEAR_SECONDS = TimeUnit.DAYS.toSeconds(365);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        String path = request.getRequestURI();
        if (path == null) {
            return;
        }

        if (path.equals("/") || path.endsWith(".html") || path.equals("/student") || path.equals("/teacher")
                || path.equals("/task-editor") || path.equals("/class-overview") || path.startsWith("/problem/")
                || (path.startsWith(OnlineJudgeWebPaths.PUBLIC_PATH)
                    && !path.startsWith(OnlineJudgeWebPaths.PUBLIC_ASSETS_PREFIX + "/"))) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            return;
        }

        if (path.startsWith("/assets/") || path.startsWith(OnlineJudgeWebPaths.PUBLIC_ASSETS_PREFIX + "/")) {
            response.setHeader("Cache-Control", "public, max-age=" + ONE_YEAR_SECONDS + ", immutable");
        }
    }
}
