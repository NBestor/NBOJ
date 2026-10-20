package com.onlinejudge.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CodePathApiPrefixFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!isPublicApiPath(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(new PrefixStrippingRequest(request), response);
    }

    private boolean isPublicApiPath(String path) {
        return OnlineJudgeWebPaths.PUBLIC_API_PREFIX.equals(path)
                || path.startsWith(OnlineJudgeWebPaths.PUBLIC_API_PREFIX + "/");
    }

    private static final class PrefixStrippingRequest extends HttpServletRequestWrapper {

        private final String requestUri;
        private final String servletPath;

        private PrefixStrippingRequest(HttpServletRequest request) {
            super(request);
            requestUri = stripPublicPrefix(request.getRequestURI());
            servletPath = stripPublicPrefix(request.getServletPath());
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer original = super.getRequestURL();
            int pathStart = original.length() - super.getRequestURI().length();
            return new StringBuffer(original.substring(0, pathStart)).append(requestUri);
        }

        private static String stripPublicPrefix(String path) {
            return path != null && path.startsWith(OnlineJudgeWebPaths.PUBLIC_PREFIX)
                    ? path.substring(OnlineJudgeWebPaths.PUBLIC_PREFIX.length())
                    : path;
        }
    }
}
