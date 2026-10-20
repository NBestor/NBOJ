package com.onlinejudge.shared.web;

import com.onlinejudge.shared.identity.YingqiSignature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OwnershipSignatureFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(YingqiSignature.OWNER_HEADER, YingqiSignature.OWNER);
        response.setHeader(YingqiSignature.SIGNATURE_HEADER, YingqiSignature.FINGERPRINT);
        response.setHeader(YingqiSignature.CLAIM_HEADER, YingqiSignature.CLAIM);
        filterChain.doFilter(request, response);
    }
}
