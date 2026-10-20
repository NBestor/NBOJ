package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeed;
import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeedCatalog;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDomainCoverage;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryQualityReport;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryQualitySummary;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryWeakTopic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiStandardLibraryQualityReportService {

    private static final Set<String> WEAK_TOPIC_PRIORITY_CODES = Set.of(
            "ALGO.SIM.STATE",
            "ALGO.SIM.PROCESS",
            "ALGO.SIM.CORNER",
            "BASIC.STRING.CHAR",
            "BASIC.STRING.MATCH",
            "BASIC.STRING.BUILD",
            "BASIC.ARRAY.TRAVERSE",
            "BASIC.ARRAY.UPDATE",
            "CONTEST.READING.INPUT",
            "CONTEST.READING.OUTPUT",
            "CONTEST.READING.CONSTRAINT",
            "CONTEST.SUBMIT.CHECKLIST"
    );

    public AiStandardLibraryQualityReport generate() {
        List<InformaticsKnowledgeSeed> knowledgeSeeds = InformaticsKnowledgeSeedCatalog.seeds();
        List<AiStandardLibrarySeed> standardSeeds = AiStandardLibrarySeedCatalog.seeds();

        Map<String, InformaticsKnowledgeSeed> knowledgeByCode = knowledgeSeeds.stream()
                .collect(Collectors.toMap(InformaticsKnowledgeSeed::code, Function.identity(), (left, right) -> left));
        List<InformaticsKnowledgeSeed> domains = knowledgeSeeds.stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.DOMAIN)
                .toList();
        List<InformaticsKnowledgeSeed> chapters = knowledgeSeeds.stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.CHAPTER)
                .toList();
        List<InformaticsKnowledgeSeed> topics = knowledgeSeeds.stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.TOPIC)
                .toList();
        List<InformaticsKnowledgeSeed> knowledgePoints = knowledgeSeeds.stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .toList();

        List<AiStandardLibrarySeed> skills = standardSeeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .toList();
        List<AiStandardLibrarySeed> mistakes = standardSeeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .toList();
        List<AiStandardLibrarySeed> handwrittenSkills = skills.stream()
                .filter(this::isHandwritten)
                .toList();
        List<AiStandardLibrarySeed> handwrittenMistakes = mistakes.stream()
                .filter(this::isHandwritten)
                .toList();
        long compatibilitySkills = skills.stream().filter(this::isCompatibility).count();
        long compatibilityMistakes = mistakes.stream().filter(this::isCompatibility).count();

        AiStandardLibraryQualitySummary summary = new AiStandardLibraryQualitySummary(
                domains.size(),
                chapters.size(),
                topics.size(),
                knowledgePoints.size(),
                skills.size(),
                mistakes.size(),
                handwrittenSkills.size(),
                handwrittenMistakes.size(),
                compatibilitySkills,
                compatibilityMistakes,
                standardSeeds.size()
        );

        List<AiStandardLibraryDomainCoverage> domainCoverage = domains.stream()
                .map(domain -> toDomainCoverage(domain, chapters, topics, knowledgePoints, skills, mistakes,
                        handwrittenSkills, handwrittenMistakes, knowledgeByCode))
                .toList();
        List<AiStandardLibraryWeakTopic> weakTopics = weakTopics(topics, knowledgePoints, handwrittenSkills, handwrittenMistakes);
        Map<String, Long> mistakeTypeDistribution = mistakes.stream()
                .collect(Collectors.groupingBy(seed -> blankTo(seed.mistakeType(), "UNKNOWN"), LinkedHashMap::new, Collectors.counting()));

        return new AiStandardLibraryQualityReport(
                summary,
                domainCoverage,
                weakTopics,
                mistakeTypeDistribution,
                recommendations(weakTopics, domainCoverage)
        );
    }

    private AiStandardLibraryDomainCoverage toDomainCoverage(InformaticsKnowledgeSeed domain,
                                                             List<InformaticsKnowledgeSeed> chapters,
                                                             List<InformaticsKnowledgeSeed> topics,
                                                             List<InformaticsKnowledgeSeed> knowledgePoints,
                                                             List<AiStandardLibrarySeed> skills,
                                                             List<AiStandardLibrarySeed> mistakes,
                                                             List<AiStandardLibrarySeed> handwrittenSkills,
                                                             List<AiStandardLibrarySeed> handwrittenMistakes,
                                                             Map<String, InformaticsKnowledgeSeed> knowledgeByCode) {
        String domainCode = domain.code();
        return new AiStandardLibraryDomainCoverage(
                domainCode,
                domain.name(),
                chapters.stream().filter(seed -> belongsToDomain(seed, domainCode)).count(),
                topics.stream().filter(seed -> belongsToDomain(seed, domainCode)).count(),
                knowledgePoints.stream().filter(seed -> belongsToDomain(seed, domainCode)).count(),
                skills.stream().filter(seed -> linkedToDomain(seed, domainCode, knowledgeByCode)).count(),
                mistakes.stream().filter(seed -> linkedToDomain(seed, domainCode, knowledgeByCode)).count(),
                handwrittenSkills.stream().mapToLong(seed -> linkCountForDomain(seed, domainCode, knowledgeByCode)).sum(),
                handwrittenMistakes.stream().mapToLong(seed -> linkCountForDomain(seed, domainCode, knowledgeByCode)).sum()
        );
    }

    private List<AiStandardLibraryWeakTopic> weakTopics(List<InformaticsKnowledgeSeed> topics,
                                                        List<InformaticsKnowledgeSeed> knowledgePoints,
                                                        List<AiStandardLibrarySeed> handwrittenSkills,
                                                        List<AiStandardLibrarySeed> handwrittenMistakes) {
        Map<String, Long> pointCountByTopic = knowledgePoints.stream()
                .collect(Collectors.groupingBy(InformaticsKnowledgeSeed::parentCode, Collectors.counting()));
        Map<String, Long> handwrittenLinksByTopic = new LinkedHashMap<>();
        for (AiStandardLibrarySeed seed : concat(handwrittenSkills, handwrittenMistakes)) {
            for (String knowledgeCode : seed.knowledgeNodeCodes()) {
                String topicCode = topicCode(knowledgeCode);
                handwrittenLinksByTopic.merge(topicCode, 1L, Long::sum);
            }
        }
        return topics.stream()
                .map(topic -> new AiStandardLibraryWeakTopic(
                        topic.code(),
                        topic.name(),
                        domainCode(topic.code()),
                        pointCountByTopic.getOrDefault(topic.code(), 0L),
                        handwrittenLinksByTopic.getOrDefault(topic.code(), 0L),
                        recommendationFor(topic.code(), topic.name())
                ))
                .filter(topic -> topic.handwrittenLinkCount() <= 1 || WEAK_TOPIC_PRIORITY_CODES.contains(topic.topicCode()))
                .sorted(Comparator
                        .comparing((AiStandardLibraryWeakTopic topic) -> !WEAK_TOPIC_PRIORITY_CODES.contains(topic.topicCode()))
                        .thenComparingLong(AiStandardLibraryWeakTopic::handwrittenLinkCount)
                        .thenComparing(AiStandardLibraryWeakTopic::topicCode))
                .limit(30)
                .toList();
    }

    private List<String> recommendations(List<AiStandardLibraryWeakTopic> weakTopics,
                                         List<AiStandardLibraryDomainCoverage> domainCoverage) {
        List<String> result = new ArrayList<>();
        if (containsTopic(weakTopics, "ALGO.SIM")) {
            result.add("优先补模拟状态、流程顺序和边界冲突的手写能力点与易错点。");
        }
        if (containsTopic(weakTopics, "BASIC.STRING")) {
            result.add("优先补字符串字符转换、查找失败、子串边界、回文和构造顺序。");
        }
        if (containsTopic(weakTopics, "BASIC.ARRAY")) {
            result.add("优先补数组遍历、同步遍历、原地覆盖、临时数组和累计更新。");
        }
        if (containsTopic(weakTopics, "CONTEST.READING") || containsTopic(weakTopics, "CONTEST.SUBMIT")) {
            result.add("优先补读题目标、数据范围、隐藏条件和提交前检查。");
        }
        domainCoverage.stream()
                .filter(domain -> domain.handwrittenMistakeLinkCount() < Math.max(6, domain.topicCount()))
                .map(domain -> domain.domainName() + "手写易错点覆盖偏少，下一轮应继续补强。")
                .forEach(result::add);
        return result.stream().distinct().toList();
    }

    private boolean containsTopic(List<AiStandardLibraryWeakTopic> weakTopics, String prefix) {
        return weakTopics.stream().anyMatch(topic -> topic.topicCode().startsWith(prefix));
    }

    private String recommendationFor(String topicCode, String topicName) {
        if (topicCode.startsWith("ALGO.SIM")) {
            return "补充状态变量、事件顺序、终止条件和特殊状态的具体错因。";
        }
        if (topicCode.startsWith("BASIC.STRING")) {
            return "补充字符转换、查找失败、子串边界、统计和构造顺序的具体错因。";
        }
        if (topicCode.startsWith("BASIC.ARRAY")) {
            return "补充遍历范围、同步遍历、原地覆盖和临时数组的具体错因。";
        }
        if (topicCode.startsWith("CONTEST.READING") || topicCode.startsWith("CONTEST.SUBMIT")) {
            return "补充目标识别、约束提取、隐藏条件和提交前复查的具体错因。";
        }
        return "补充「" + topicName + "」的手写能力点和真实易错点。";
    }

    private boolean belongsToDomain(InformaticsKnowledgeSeed seed, String domainCode) {
        return domainCode(seed.code()).equals(domainCode);
    }

    private boolean linkedToDomain(AiStandardLibrarySeed seed,
                                   String domainCode,
                                   Map<String, InformaticsKnowledgeSeed> knowledgeByCode) {
        return seed.knowledgeNodeCodes().stream()
                .anyMatch(code -> domainCode(code).equals(domainCode)
                        || OptionalKnowledge.parentDomain(code, knowledgeByCode).equals(domainCode));
    }

    private long linkCountForDomain(AiStandardLibrarySeed seed,
                                    String domainCode,
                                    Map<String, InformaticsKnowledgeSeed> knowledgeByCode) {
        return seed.knowledgeNodeCodes().stream()
                .filter(code -> domainCode(code).equals(domainCode)
                        || OptionalKnowledge.parentDomain(code, knowledgeByCode).equals(domainCode))
                .count();
    }

    private String topicCode(String knowledgeCode) {
        String[] parts = knowledgeCode.split("\\.");
        if (parts.length < 3) {
            return knowledgeCode;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private String domainCode(String code) {
        int dot = code.indexOf('.');
        return dot < 0 ? code : code.substring(0, dot);
    }

    private boolean isHandwritten(AiStandardLibrarySeed seed) {
        return !isCompatibility(seed) && !isGenerated(seed);
    }

    private boolean isGenerated(AiStandardLibrarySeed seed) {
        String code = seed.code() == null ? "" : seed.code();
        return code.matches("^(SK|MP)_[A-Z0-9_]+_[0-9A-F]{6,}$");
    }

    private boolean isCompatibility(AiStandardLibrarySeed seed) {
        return seed.code().startsWith("SK_COMPAT_") || seed.category().startsWith("兼容");
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
    }

    private List<AiStandardLibrarySeed> concat(List<AiStandardLibrarySeed> left, List<AiStandardLibrarySeed> right) {
        List<AiStandardLibrarySeed> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    private static final class OptionalKnowledge {
        private OptionalKnowledge() {
        }

        static String parentDomain(String code, Map<String, InformaticsKnowledgeSeed> knowledgeByCode) {
            InformaticsKnowledgeSeed seed = knowledgeByCode.get(code);
            if (seed == null || seed.parentCode() == null || seed.parentCode().isBlank()) {
                return "";
            }
            return seed.parentCode().split("\\.")[0];
        }
    }
}
