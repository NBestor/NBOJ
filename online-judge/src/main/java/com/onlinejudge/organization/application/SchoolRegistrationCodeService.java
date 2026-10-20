package com.onlinejudge.organization.application;

import com.onlinejudge.shared.security.CryptoSupport;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SchoolRegistrationCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "SCH-" + CryptoSupport.base64Url(bytes);
    }

    public String hash(String code) {
        return CryptoSupport.sha256(code == null ? "" : code.trim());
    }
}
