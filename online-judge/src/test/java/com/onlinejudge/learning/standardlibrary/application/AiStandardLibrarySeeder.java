package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.content-migration", name = "enabled", havingValue = "true")
public class AiStandardLibrarySeeder implements CommandLineRunner {

    private final AiStandardLibraryItemRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;
        for (AiStandardLibrarySeed seed : AiStandardLibrarySeedCatalog.seeds()) {
            if (repository.existsByLayerAndCode(seed.layer(), seed.code())) {
                continue;
            }
            repository.save(toEntity(seed));
            inserted++;
        }
        int disabledFallbackItems = disableGeneratedFallbackItems();
        if (inserted + disabledFallbackItems > 0) {
            log.info("Seeded {} AI standard library items, disabled archived fallback items={}",
                    inserted, disabledFallbackItems);
        }
    }

    private int disableGeneratedFallbackItems() {
        List<AiStandardLibraryItem> fallbackItems = repository.findAllByOrderByLayerAscCategoryAscCodeAsc().stream()
                .filter(AiStandardLibraryItem::isEnabled)
                .filter(item -> AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(item.getLayer(), item.getCode()))
                .toList();
        fallbackItems.forEach(item -> item.setEnabled(false));
        repository.saveAll(fallbackItems);
        return fallbackItems.size();
    }

    private AiStandardLibraryItem toEntity(AiStandardLibrarySeed seed) {
        return AiStandardLibraryItem.builder()
                .layer(seed.layer())
                .code(seed.code())
                .category(seed.category())
                .name(seed.name())
                .description(seed.description())
                .studentExplanation(seed.studentExplanation())
                .teacherExplanation(seed.teacherExplanation())
                .skillUnitCode(seed.skillUnitCode())
                .primaryKnowledgeNodeCode(primaryKnowledgeNode(seed.knowledgeNodeCodes()))
                .mistakeType(seed.mistakeType())
                .commonMisconception(seed.commonMisconception())
                .evidenceSignals(join(seed.evidenceSignals()))
                .commonCodePatterns(join(seed.commonCodePatterns()))
                .judgeSignals(join(seed.judgeSignals()))
                .requiredEvidence(join(seed.requiredEvidence()))
                .whenToUse(seed.whenToUse())
                .studentBenefit(seed.studentBenefit())
                .hintL1(seed.hintL1())
                .hintL2(seed.hintL2())
                .hintL3(seed.hintL3())
                .abilityPoint(seed.abilityPoint())
                .severity(seed.severity())
                .applicableLanguages(join(seed.applicableLanguages()))
                .relatedItems(join(seed.relatedItems()))
                .knowledgeNodeCodes(join(seed.knowledgeNodeCodes()))
                .relatedKnowledgeNodeCodes(join(relatedKnowledgeNodes(seed.knowledgeNodeCodes())))
                .prerequisiteKnowledgeCodes(join(seed.prerequisiteKnowledgeCodes()))
                .teachingAction(seed.teachingAction())
                .enabled(true)
                .libraryVersion(seed.libraryVersion())
                .build();
    }

    private String primaryKnowledgeNode(List<String> knowledgeNodeCodes) {
        if (knowledgeNodeCodes == null) {
            return "";
        }
        return knowledgeNodeCodes.stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private List<String> relatedKnowledgeNodes(List<String> knowledgeNodeCodes) {
        String primary = primaryKnowledgeNode(knowledgeNodeCodes);
        if (primary.isBlank() || knowledgeNodeCodes == null) {
            return List.of();
        }
        return knowledgeNodeCodes.stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .filter(value -> !value.equals(primary))
                .distinct()
                .toList();
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values.stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
