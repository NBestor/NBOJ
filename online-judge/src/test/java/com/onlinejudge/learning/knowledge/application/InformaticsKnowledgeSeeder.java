package com.onlinejudge.learning.knowledge.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.content-migration", name = "enabled", havingValue = "true")
public class InformaticsKnowledgeSeeder implements CommandLineRunner {

    private final InformaticsKnowledgeNodeRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;
        int updated = 0;
        for (InformaticsKnowledgeSeed seed : InformaticsKnowledgeSeedCatalog.seeds()) {
            InformaticsKnowledgeNode node = repository.findByCode(seed.code()).orElse(null);
            if (node == null) {
                repository.save(toEntity(seed));
                inserted++;
            } else if (apply(node, seed)) {
                repository.save(node);
                updated++;
            }
        }
        if (inserted + updated > 0) {
            log.info("Seeded {} informatics knowledge nodes, updated {} existing nodes", inserted, updated);
        }
    }

    private boolean apply(InformaticsKnowledgeNode node, InformaticsKnowledgeSeed seed) {
        boolean changed = false;
        changed |= update(node::getParentCode, node::setParentCode, blankToNull(seed.parentCode()));
        changed |= update(node::getType, node::setType, seed.type());
        changed |= update(node::getName, node::setName, seed.name());
        changed |= update(node::getDescription, node::setDescription, seed.description());
        changed |= update(node::getPath, node::setPath, seed.path());
        changed |= update(node::getStage, node::setStage, seed.stage());
        changed |= update(node::getDifficulty, node::setDifficulty, seed.difficulty());
        changed |= update(node::getAliases, node::setAliases, join(seed.aliases()));
        changed |= update(node::getPrerequisites, node::setPrerequisites, join(seed.prerequisites()));
        changed |= update(node::getLearningObjectives, node::setLearningObjectives, join(seed.learningObjectives()));
        changed |= update(node::getTypicalProblems, node::setTypicalProblems, join(seed.typicalProblems()));
        changed |= update(node::getSortOrder, node::setSortOrder, seed.sortOrder());
        changed |= update(node::getLibraryVersion, node::setLibraryVersion, seed.libraryVersion());
        if (!node.isEnabled()) {
            node.setEnabled(true);
            changed = true;
        }
        return changed;
    }

    private <T> boolean update(java.util.function.Supplier<T> getter,
                               java.util.function.Consumer<T> setter,
                               T value) {
        T current = getter.get();
        if (java.util.Objects.equals(current, value)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private InformaticsKnowledgeNode toEntity(InformaticsKnowledgeSeed seed) {
        return InformaticsKnowledgeNode.builder()
                .code(seed.code())
                .parentCode(blankToNull(seed.parentCode()))
                .type(seed.type())
                .name(seed.name())
                .description(seed.description())
                .path(seed.path())
                .stage(seed.stage())
                .difficulty(seed.difficulty())
                .aliases(join(seed.aliases()))
                .prerequisites(join(seed.prerequisites()))
                .learningObjectives(join(seed.learningObjectives()))
                .typicalProblems(join(seed.typicalProblems()))
                .sortOrder(seed.sortOrder())
                .enabled(true)
                .libraryVersion(seed.libraryVersion())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values);
    }
}
