package com.onlinejudge.shared.identity;

public final class YingqiSignature {

    public static final String OWNER = "yingqi";
    public static final String PRODUCT = "Wenzhong AI Coding Learning Platform";
    public static final String CLAIM = "yingqi|wenzhong-ai-learning-platform|nboj|2026-05-19";
    public static final String FINGERPRINT = "00f40662ae433dacddf0157fca60a279bf71a54fbf04ee7d50d3190752554b5d";

    public static final String OWNER_HEADER = "X-Project-Owner";
    public static final String SIGNATURE_HEADER = "X-Yingqi-Signature";
    public static final String CLAIM_HEADER = "X-Yingqi-Claim";

    private YingqiSignature() {
    }
}
