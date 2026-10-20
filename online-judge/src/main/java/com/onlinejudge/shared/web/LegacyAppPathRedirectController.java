package com.onlinejudge.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

@Controller
public class LegacyAppPathRedirectController {
    @GetMapping({
            OnlineJudgeWebPaths.LEGACY_APP_PREFIX,
            OnlineJudgeWebPaths.LEGACY_APP_PREFIX + "/",
            OnlineJudgeWebPaths.LEGACY_APP_PREFIX + "/**"
    })
    public ResponseEntity<Void> redirectToCodePath(HttpServletRequest request) {
        String suffix = request.getRequestURI().substring(OnlineJudgeWebPaths.LEGACY_APP_PREFIX.length());
        String target = OnlineJudgeWebPaths.PUBLIC_PREFIX + suffix;
        if (request.getQueryString() != null) {
            target += "?" + request.getQueryString();
        }
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header(HttpHeaders.LOCATION, URI.create(target).toASCIIString())
                .build();
    }
}
