package com.onlinejudge.submission.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EvidenceRefSupport {

    private static final int MAX_CODE_LINE_EVIDENCE_REFS = 500;
    private static final Pattern CODE_LINE = Pattern.compile("^code:line:(\\d+)$");
    private static final Pattern CODE_LINE_WORD = Pattern.compile("^code:line[_-](\\d+)(?:[_-].*)?$");
    private static final Pattern CODE_LETTER_LINE = Pattern.compile("^code:l(\\d+)$");
    private static final Pattern CODE_RANGE = Pattern.compile("^code:range:(\\d+)-(\\d+)$");
    private static final Pattern CODE_BARE_LINE = Pattern.compile("^code:(\\d+)$");
    private static final Pattern CODE_BARE_RANGE = Pattern.compile("^code:(\\d+)-(\\d+)$");
    private static final Pattern CODE_LINE_RANGE = Pattern.compile("^code:line:(\\d+)-(\\d+)$");
    private static final Pattern CODE_LETTER_RANGE = Pattern.compile("^code:l(\\d+)-l?(\\d+)$");
    private static final Pattern SHORT_LINE = Pattern.compile("^line:(\\d+)$");
    private static final Pattern SHORT_LINES = Pattern.compile("^lines?:(\\d+)-(\\d+)$");
    private static final Pattern SOURCE_LINE = Pattern.compile("^source:line:?(\\d+)$");
    private static final Pattern SOURCE_LINES = Pattern.compile("^source:lines?:(\\d+)-(\\d+)$");
    private static final Pattern SOURCE_BARE_LINE = Pattern.compile("^source:(\\d+)$");
    private static final Pattern SOURCE_BARE_RANGE = Pattern.compile("^source:(\\d+)-(\\d+)$");

    private EvidenceRefSupport() {
    }

    static Set<String> validEvidenceRefs(ModelDiagnosisBrief brief) {
        return new LinkedHashSet<>(orderedEvidenceRefs(brief));
    }

    static List<String> orderedEvidenceRefs(ModelDiagnosisBrief brief) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (brief == null) {
            return List.of();
        }
        if (brief.getEvidenceRefs() != null) {
            brief.getEvidenceRefs().forEach(ref -> {
                if (ref != null && !ref.isBlank()) {
                    refs.add(ref.trim());
                }
            });
        }
        int lineLimit = sourceLineLimit(brief);
        for (int line = 1; line <= lineLimit; line++) {
            refs.add("code:line:" + line);
        }
        return List.copyOf(refs);
    }

    static boolean isValidEvidenceRef(String rawRef, ModelDiagnosisBrief brief) {
        String normalized = normalizeEvidenceRef(rawRef, brief);
        if (normalized.isBlank()) {
            return false;
        }
        Set<String> refs = validEvidenceRefs(brief);
        return refs.contains(normalized) || validCodeRange(normalized, brief);
    }

    static String normalizeEvidenceRef(String rawRef, ModelDiagnosisBrief brief) {
        return normalizeEvidenceRef(rawRef, validEvidenceRefs(brief), orderedEvidenceRefs(brief), brief);
    }

    static String normalizeEvidenceRef(String rawRef,
                                       Set<String> validRefs,
                                       List<String> orderedValidRefs,
                                       ModelDiagnosisBrief brief) {
        if (rawRef == null || rawRef.isBlank()) {
            return "";
        }
        String trimmed = rawRef.trim();
        if (validRefs != null && validRefs.contains(trimmed)) {
            return trimmed;
        }
        String canonicalCodeRef = canonicalCodeRef(trimmed, brief);
        if (!canonicalCodeRef.isBlank()) {
            return canonicalCodeRef;
        }
        String canonicalJudgeRef = canonicalJudgeRef(trimmed, validRefs);
        if (!canonicalJudgeRef.isBlank()) {
            return canonicalJudgeRef;
        }
        String relaxed = firstEvidenceThatMatchesRelaxedRef(orderedValidRefs, trimmed);
        return relaxed.isBlank() ? trimmed : relaxed;
    }

    static List<String> normalizeEvidenceRefs(List<String> refs,
                                              Set<String> validRefs,
                                              List<String> orderedValidRefs,
                                              ModelDiagnosisBrief brief,
                                              List<String> softFixes) {
        if (refs == null || refs.isEmpty()) {
            return refs;
        }
        List<String> normalizedRefs = new ArrayList<>(refs);
        for (int i = 0; i < refs.size(); i++) {
            String rawRef = refs.get(i);
            String normalized = normalizeEvidenceRef(rawRef, validRefs, orderedValidRefs, brief);
            if (normalized.isBlank()) {
                continue;
            }
            normalizedRefs.set(i, normalized);
            if (softFixes != null && rawRef != null && !rawRef.trim().equals(normalized)) {
                softFixes.add("evidenceRef alias " + rawRef + " -> " + normalized);
            }
        }
        return normalizedRefs.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .toList();
    }

    static String invalidEvidenceRefs(List<String> refs,
                                      Set<String> validRefs,
                                      ModelDiagnosisBrief brief,
                                      String field,
                                      boolean required) {
        if (refs == null || refs.isEmpty()) {
            return required ? field + " is empty." : "";
        }
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                return field + " contains blank evidenceRef.";
            }
            String normalized = normalizeEvidenceRef(ref, validRefs, orderedEvidenceRefs(brief), brief);
            boolean allowedBySet = validRefs != null && validRefs.contains(normalized);
            boolean allowedByRange = validCodeRange(normalized, brief);
            if (normalized.isBlank() || (!allowedBySet && !allowedByRange)) {
                return field + " contains invalid evidenceRef=" + ref;
            }
        }
        return "";
    }

    private static String canonicalCodeRef(String rawRef, ModelDiagnosisBrief brief) {
        String normalized = rawRef.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if ("keycodeexcerpt".equals(normalized)) {
            return wholeCodeRef(brief);
        }
        normalized = normalized.replaceFirst("^keycodeexcerpt:", "code:");
        Matcher line = CODE_LINE.matcher(normalized);
        if (line.matches()) {
            int value = parsePositiveInt(line.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher wordLine = CODE_LINE_WORD.matcher(normalized);
        if (wordLine.matches()) {
            int value = parsePositiveInt(wordLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher letterLine = CODE_LETTER_LINE.matcher(normalized);
        if (letterLine.matches()) {
            int value = parsePositiveInt(letterLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher bareLine = CODE_BARE_LINE.matcher(normalized);
        if (bareLine.matches()) {
            int value = parsePositiveInt(bareLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher shortLine = SHORT_LINE.matcher(normalized);
        if (shortLine.matches()) {
            int value = parsePositiveInt(shortLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher sourceLine = SOURCE_LINE.matcher(normalized);
        if (sourceLine.matches()) {
            int value = parsePositiveInt(sourceLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher range = CODE_RANGE.matcher(normalized);
        if (range.matches()) {
            return canonicalRange(range.group(1), range.group(2), brief);
        }
        Matcher bareRange = CODE_BARE_RANGE.matcher(normalized);
        if (bareRange.matches()) {
            return canonicalRange(bareRange.group(1), bareRange.group(2), brief);
        }
        Matcher lineRange = CODE_LINE_RANGE.matcher(normalized);
        if (lineRange.matches()) {
            return canonicalRange(lineRange.group(1), lineRange.group(2), brief);
        }
        Matcher letterRange = CODE_LETTER_RANGE.matcher(normalized);
        if (letterRange.matches()) {
            return canonicalRange(letterRange.group(1), letterRange.group(2), brief);
        }
        Matcher shortLines = SHORT_LINES.matcher(normalized);
        if (shortLines.matches()) {
            return canonicalRange(shortLines.group(1), shortLines.group(2), brief);
        }
        Matcher sourceLines = SOURCE_LINES.matcher(normalized);
        if (sourceLines.matches()) {
            return canonicalRange(sourceLines.group(1), sourceLines.group(2), brief);
        }
        Matcher sourceBareLine = SOURCE_BARE_LINE.matcher(normalized);
        if (sourceBareLine.matches()) {
            int value = parsePositiveInt(sourceBareLine.group(1));
            return validCodeLine(value, brief) ? "code:line:" + value : "";
        }
        Matcher sourceBareRange = SOURCE_BARE_RANGE.matcher(normalized);
        if (sourceBareRange.matches()) {
            return canonicalRange(sourceBareRange.group(1), sourceBareRange.group(2), brief);
        }
        return "";
    }

    private static String canonicalJudgeRef(String rawRef, Set<String> validRefs) {
        if (validRefs == null || !validRefs.contains("judge:first_failed_case")) {
            return "";
        }
        String normalized = rawRef == null
                ? ""
                : rawRef.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (List.of("visiblecasefacts", "visiblecasefact", "visiblecases", "visiblecase",
                "firstfailedcase", "firstfailedtest", "failedcase").contains(normalized)) {
            return "judge:first_failed_case";
        }
        if (normalized.matches("^judge:(case|first_failed_case)[_:\\-]?\\d*([_:\\-].*)?$")) {
            return "judge:first_failed_case";
        }
        return "";
    }

    private static String wholeCodeRef(ModelDiagnosisBrief brief) {
        int lineLimit = sourceLineLimit(brief);
        if (lineLimit <= 0) {
            return "";
        }
        return lineLimit == 1 ? "code:line:1" : "code:range:1-" + lineLimit;
    }

    private static String canonicalRange(String startText, String endText, ModelDiagnosisBrief brief) {
        int start = parsePositiveInt(startText);
        int end = parsePositiveInt(endText);
        if (start <= 0 || end < start || !validCodeLine(start, brief) || !validCodeLine(end, brief)) {
            return "";
        }
        return "code:range:" + start + "-" + end;
    }

    private static boolean validCodeRange(String ref, ModelDiagnosisBrief brief) {
        Matcher range = CODE_RANGE.matcher(ref == null ? "" : ref.trim());
        if (!range.matches()) {
            return false;
        }
        int start = parsePositiveInt(range.group(1));
        int end = parsePositiveInt(range.group(2));
        return start > 0 && end >= start && validCodeLine(start, brief) && validCodeLine(end, brief);
    }

    private static boolean validCodeLine(int line, ModelDiagnosisBrief brief) {
        return line > 0 && line <= sourceLineLimit(brief);
    }

    private static int sourceLineLimit(ModelDiagnosisBrief brief) {
        if (brief == null || brief.getSourceCodeLineCount() == null || brief.getSourceCodeLineCount() <= 0) {
            return 0;
        }
        return Math.min(brief.getSourceCodeLineCount(), MAX_CODE_LINE_EVIDENCE_REFS);
    }

    private static String firstEvidenceThatMatchesRelaxedRef(List<String> evidenceRefs, String rawRef) {
        if (evidenceRefs == null || evidenceRefs.isEmpty() || rawRef == null || rawRef.isBlank()) {
            return "";
        }
        String trimmed = rawRef.trim();
        return evidenceRefs.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .filter(ref -> trimmed.startsWith(ref + ":") || ref.startsWith(trimmed + ":"))
                .findFirst()
                .orElse("");
    }

    private static int parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
