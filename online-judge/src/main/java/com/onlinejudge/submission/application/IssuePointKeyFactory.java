package com.onlinejudge.submission.application;

import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class IssuePointKeyFactory {

    public static final String VERSION = "point-key-v1";

    public Identity identity(SubmissionDiagnosisFact fact, List<String> knowledgePath) {
        if (fact == null) {
            return new Identity("text:" + VERSION + ":empty", "TEXT_FINGERPRINT", VERSION);
        }
        return identity(
                fact.getFactType(),
                fact.getMistakePointId(),
                fact.getImprovementPointId(),
                fact.getProvisionalNodeCode(),
                fact.getSkillUnitId(),
                knowledgePath,
                fact.getTitle()
        );
    }

    public Identity identity(
            String factType,
            String mistakePointId,
            String improvementPointId,
            String skillUnitId,
            List<String> knowledgePath,
            String title
    ) {
        return identity(
                factType,
                mistakePointId,
                improvementPointId,
                null,
                skillUnitId,
                knowledgePath,
                title
        );
    }

    public Identity identity(
            String factType,
            String mistakePointId,
            String improvementPointId,
            String provisionalNodeCode,
            String skillUnitId,
            List<String> knowledgePath,
            String title
    ) {
        if (hasText(mistakePointId)) {
            return formal("mistake", mistakePointId);
        }
        if (hasText(improvementPointId)) {
            return formal("improvement", improvementPointId);
        }
        if (hasText(provisionalNodeCode)) {
            return new Identity(
                    "provisional:" + VERSION + ":" + normalizeId(provisionalNodeCode),
                    "PROVISIONAL_ID",
                    VERSION
            );
        }
        if (hasText(skillUnitId)) {
            String value = normalizeId(skillUnitId) + ":" + normalizeId(factType);
            return new Identity("skill:" + VERSION + ":" + value, "SKILL_UNIT", VERSION);
        }
        String pathLeaf = safe(knowledgePath).stream()
                .filter(this::hasText)
                .reduce((left, right) -> right)
                .orElse("");
        String normalized = normalizeText(pathLeaf) + "|" + normalizeText(title) + "|" + normalizeId(factType);
        return new Identity("text:" + VERSION + ":" + sha256(normalized), "TEXT_FINGERPRINT", VERSION);
    }

    public String sourceFingerprint(String sourceCode) {
        String normalized = sourceCode == null ? "" : sourceCode.replace("\r\n", "\n").replace('\r', '\n').trim();
        return sha256(normalized);
    }

    private Identity formal(String namespace, String value) {
        return new Identity(namespace + ":" + VERSION + ":" + normalizeId(value), "FORMAL_ID", VERSION);
    }

    private String normalizeId(String value) {
        return normalizeText(value).replace(' ', '-');
    }

    String normalizeText(String value) {
        if (!hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成问题身份指纹", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record Identity(String key, String source, String version) {
    }
}
