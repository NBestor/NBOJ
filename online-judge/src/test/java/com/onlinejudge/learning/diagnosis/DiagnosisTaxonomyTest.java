package com.onlinejudge.learning.diagnosis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisTaxonomyTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();

    @Test
    void normalizesOnlyKnownFineGrainedTags() {
        List<String> tags = taxonomy.normalizeFineGrainedTags(List.of(
                "off_by_one",
                "OUTPUT_FORMAT_DETAIL",
                "LOOP_BOUNDARY",
                "UNKNOWN_TAG"
        ));

        assertThat(tags).containsExactly("OFF_BY_ONE", "OUTPUT_FORMAT_DETAIL");
    }

    @Test
    void fineGrainedTagKeepsParentAndTeachingMetadata() {
        DiagnosisTaxonomy.DiagnosisTag tag = taxonomy.get("sample_overfit");

        assertThat(tag).isNotNull();
        assertThat(tag.isFineGrained()).isTrue();
        assertThat(tag.getParentTag()).isEqualTo("SAMPLE_ONLY");
        assertThat(tag.getStudentExplanation()).contains("样例");
    }

    @Test
    void tagsExposeTeachingActionsForHintPlanning() {
        assertThat(taxonomy.teachingAction("OFF_BY_ONE")).isEqualTo("TRACE_VARIABLES");
        assertThat(taxonomy.teachingAction("BRUTE_FORCE_LIMIT")).isEqualTo("COUNT_COMPLEXITY");
        assertThat(taxonomy.teachingAction("GREEDY_ASSUMPTION")).isEqualTo("CHECK_INVARIANT");
        assertThat(taxonomy.teachingAction("UNKNOWN_TAG")).isEqualTo("TRACE_VARIABLES");
    }

    @Test
    void includesNewFineGrainedTagsFromLiveEvaluationGaps() {
        assertThat(taxonomy.normalizeFineGrainedTags(List.of(
                "RECURSION_BASE_CASE",
                "BINARY_SEARCH_BOUNDARY",
                "SORT_KEY",
                "INTEGER_DIVISION_PRECISION",
                "ARRAY_INDEX_OUT_OF_BOUNDS",
                "UNINITIALIZED_VARIABLE"
        ))).containsExactly(
                "RECURSION_BASE_CASE",
                "BINARY_SEARCH_BOUNDARY",
                "SORT_KEY",
                "INTEGER_DIVISION_PRECISION",
                "ARRAY_INDEX_OUT_OF_BOUNDS",
                "UNINITIALIZED_VARIABLE"
        );
        assertThat(taxonomy.get("RECURSION_BASE_CASE").getParentTag()).isEqualTo("RECURSION_EXIT");
        assertThat(taxonomy.get("BINARY_SEARCH_BOUNDARY").getParentTag()).isEqualTo("LOOP_BOUNDARY");
        assertThat(taxonomy.get("SORT_KEY").getParentTag()).isEqualTo("ALGORITHM_STRATEGY");
        assertThat(taxonomy.get("INTEGER_DIVISION_PRECISION").getParentTag()).isEqualTo("BOUNDARY_CONDITION");
        assertThat(taxonomy.get("ARRAY_INDEX_OUT_OF_BOUNDS").getParentTag()).isEqualTo("RUNTIME_STABILITY");
        assertThat(taxonomy.get("UNINITIALIZED_VARIABLE").getParentTag()).isEqualTo("VARIABLE_INITIALIZATION");
    }
}
