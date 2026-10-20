package com.onlinejudge.classroom.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.StudentIdentityService;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.ImportCommitResponse;
import com.onlinejudge.classroom.dto.ImportPreviewResponse;
import com.onlinejudge.classroom.dto.ImportRequest;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.problem.application.ProblemService;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.dto.CreateProblemRequest;
import com.onlinejudge.problem.persistence.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ClassroomImportService {

    private final ClassGroupRepository classGroupRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ProblemRepository problemRepository;
    private final ProblemService problemService;
    private final ObjectMapper objectMapper;
    private final StudentIdentityService studentIdentityService;

    @Autowired(required = false)
    private CurrentTeacherContext currentTeacherContext;

    public ImportPreviewResponse previewStudents(ImportRequest request) {
        List<ImportPreviewResponse.RowIssue> issues = new ArrayList<>();
        List<ImportPreviewResponse.StudentImportRow> rows = parseStudentRows(request, issues);
        return ImportPreviewResponse.builder()
                .importType("students")
                .totalRows(rows.size())
                .validRows((int) rows.stream().filter(ImportPreviewResponse.StudentImportRow::isValid).count())
                .invalidRows((int) rows.stream().filter(row -> !row.isValid()).count())
                .duplicateRows((int) rows.stream().filter(ImportPreviewResponse.StudentImportRow::isDuplicate).count())
                .message(rows.isEmpty() ? "没有识别到学生名单，请检查格式。" : "学生名单解析完成，请确认后导入。")
                .issues(issues)
                .students(rows)
                .problems(List.of())
                .build();
    }

    @Transactional
    public ImportCommitResponse commitStudents(ImportRequest request) {
        ImportPreviewResponse preview = previewStudents(request);
        List<Long> createdIds = new ArrayList<>();
        List<ImportPreviewResponse.RowIssue> issues = new ArrayList<>(preview.getIssues());
        int skipped = 0;
        int failed = 0;
        int updated = 0;

        for (ImportPreviewResponse.StudentImportRow row : preview.getStudents()) {
            if (!row.isValid() || row.isDuplicate()) {
                skipped++;
                continue;
            }
            try {
                ClassGroup classGroup = resolveClassGroup(row.getClassGroupId(), row.getClassName());
                String identityKey = studentIdentityService.buildStableIdentityKey(classGroup.getId(), row.getClassName(), row.getDisplayName(), row.getStudentNo());
                StudentProfile existing = studentProfileRepository
                        .findFirstByClassGroupIdAndStudentNoIgnoreCase(classGroup.getId(), row.getStudentNo()).orElse(null);
                StudentProfile student = existing == null ? StudentProfile.builder().identityKey(identityKey).build() : existing;
                student.setClassGroupId(classGroup.getId());
                student.setDisplayName(row.getDisplayName());
                student.setStudentNo(row.getStudentNo());
                student.setNote(row.getNote());
                student.setStatus(StudentProfile.RosterStatus.ACTIVE);
                createdIds.add(studentProfileRepository.save(student).getId());
                if (existing != null) updated++;
            } catch (Exception exception) {
                failed++;
                issues.add(issue(row.getRowNumber(), "error", exception.getMessage()));
            }
        }

        return ImportCommitResponse.builder()
                .importType("students")
                .createdCount(createdIds.size())
                .updatedCount(updated)
                .skippedCount(skipped)
                .failedCount(failed)
                .createdIds(createdIds)
                .issues(issues)
                .message("学生名单导入完成：新增/更新 " + createdIds.size() + " 人，跳过 " + skipped + " 行。")
                .build();
    }

    public ImportPreviewResponse previewProblems(ImportRequest request) {
        List<ImportPreviewResponse.RowIssue> issues = new ArrayList<>();
        List<ImportPreviewResponse.ProblemImportRow> rows = parseProblemRows(request, issues);
        return ImportPreviewResponse.builder()
                .importType("problems")
                .totalRows(rows.size())
                .validRows((int) rows.stream().filter(ImportPreviewResponse.ProblemImportRow::isValid).count())
                .invalidRows((int) rows.stream().filter(row -> !row.isValid()).count())
                .duplicateRows((int) rows.stream().filter(ImportPreviewResponse.ProblemImportRow::isDuplicate).count())
                .message(rows.isEmpty() ? "没有识别到题目，请检查格式。" : "题目解析完成，请确认后导入。")
                .issues(issues)
                .students(List.of())
                .problems(rows)
                .build();
    }

    @Transactional
    public ImportCommitResponse commitProblems(ImportRequest request) {
        ImportPreviewResponse preview = previewProblems(request);
        List<Long> createdIds = new ArrayList<>();
        List<ImportPreviewResponse.RowIssue> issues = new ArrayList<>(preview.getIssues());
        int skipped = 0;
        int failed = 0;

        for (ImportPreviewResponse.ProblemImportRow row : preview.getProblems()) {
            if (!row.isValid() || row.isDuplicate()) {
                skipped++;
                continue;
            }
            try {
                CreateProblemRequest createRequest = objectMapper.readValue(row.getPayloadJson(), CreateProblemRequest.class);
                createdIds.add(problemService.createProblem(createRequest).getId());
            } catch (Exception exception) {
                failed++;
                issues.add(issue(row.getRowNumber(), "error", exception.getMessage()));
            }
        }

        return ImportCommitResponse.builder()
                .importType("problems")
                .createdCount(createdIds.size())
                .updatedCount(0)
                .skippedCount(skipped)
                .failedCount(failed)
                .createdIds(createdIds)
                .issues(issues)
                .message("题目导入完成：新增 " + createdIds.size() + " 题，跳过 " + skipped + " 行。")
                .build();
    }

    private List<ImportPreviewResponse.StudentImportRow> parseStudentRows(ImportRequest request,
                                                                          List<ImportPreviewResponse.RowIssue> issues) {
        String content = normalizeContent(request);
        String format = normalizeFormat(request, content);
        if ("xlsx".equals(format)) {
            content = extractXlsxAsDelimitedText(request, issues);
            if (content.isBlank()) {
                return List.of();
            }
        }

        List<ImportPreviewResponse.StudentImportRow> rows = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int start = hasStudentHeader(lines) ? 1 : 0;
        for (int index = start; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCells(line);
            String className = firstNonBlank(get(cells, 0), request.getClassName(), "温中信息技术试点班");
            String displayName = cells.size() == 1 ? get(cells, 0) : get(cells, 1);
            String studentNo = cells.size() >= 3 ? get(cells, 2) : "";
            String note = cells.size() >= 4 ? get(cells, 3) : "";
            if (cells.size() == 2) {
                displayName = get(cells, 0);
                studentNo = get(cells, 1);
            }
            studentNo = studentNo.trim().toUpperCase(Locale.ROOT);
            String message = "";
            boolean valid = true;
            if (displayName.isBlank()) {
                valid = false;
                message = "缺少姓名";
                issues.add(issue(index + 1, "error", message));
            }
            if (studentNo.isBlank()) {
                valid = false;
                message = "缺少学号";
                issues.add(issue(index + 1, "error", message));
            }
            String dedupeKey = (request.getClassGroupId() == null ? className : String.valueOf(request.getClassGroupId()))
                    + "|"
                    + (studentNo.isBlank() ? displayName.toLowerCase(Locale.ROOT) : studentNo.toLowerCase(Locale.ROOT));
            boolean duplicate = !seenKeys.add(dedupeKey);
            if (duplicate) {
                message = "名单中重复";
                issues.add(issue(index + 1, "warning", message));
            }

            rows.add(ImportPreviewResponse.StudentImportRow.builder()
                    .rowNumber(index + 1)
                    .className(className)
                    .classGroupId(request.getClassGroupId())
                    .displayName(displayName)
                    .studentNo(studentNo)
                    .note(note)
                    .valid(valid)
                    .duplicate(duplicate)
                    .message(message)
                    .build());
        }
        return rows;
    }

    private List<ImportPreviewResponse.ProblemImportRow> parseProblemRows(ImportRequest request,
                                                                          List<ImportPreviewResponse.RowIssue> issues) {
        String content = normalizeContent(request);
        String format = normalizeFormat(request, content);
        if ("xlsx".equals(format)) {
            content = extractXlsxAsDelimitedText(request, issues);
            if (content.isBlank()) {
                return List.of();
            }
            format = "csv";
        }
        if ("json".equals(format) || content.trim().startsWith("{") || content.trim().startsWith("[")) {
            return parseProblemJson(content, issues);
        }
        if ("markdown".equals(format) || content.contains("##") || content.contains("# ")) {
            return parseProblemMarkdown(content, issues);
        }
        return parseProblemCsv(content, issues);
    }

    private List<ImportPreviewResponse.ProblemImportRow> parseProblemJson(String content,
                                                                          List<ImportPreviewResponse.RowIssue> issues) {
        List<ImportPreviewResponse.ProblemImportRow> rows = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode array = root.isArray() ? root : root.has("problems") ? root.get("problems") : objectMapper.createArrayNode().add(root);
            int rowNumber = 1;
            for (JsonNode node : array) {
                CreateProblemRequest request = objectMapper.treeToValue(node, CreateProblemRequest.class);
                rows.add(toProblemRow(rowNumber++, request, issues));
            }
        } catch (Exception exception) {
            issues.add(issue(1, "error", "JSON 解析失败：" + exception.getMessage()));
        }
        return markProblemDuplicates(rows);
    }

    private List<ImportPreviewResponse.ProblemImportRow> parseProblemMarkdown(String content,
                                                                               List<ImportPreviewResponse.RowIssue> issues) {
        CreateProblemRequest request = new CreateProblemRequest();
        request.setTitle(extractMarkdownTitle(content));
        request.setDescription(content.trim());
        request.setDifficulty(Problem.Difficulty.EASY);
        request.setTimeLimit(1000);
        request.setMemoryLimit(128000);
        request.setTestCases(extractMarkdownSamples(content));
        return markProblemDuplicates(List.of(toProblemRow(1, request, issues)));
    }

    private List<ImportPreviewResponse.ProblemImportRow> parseProblemCsv(String content,
                                                                         List<ImportPreviewResponse.RowIssue> issues) {
        List<ImportPreviewResponse.ProblemImportRow> rows = new ArrayList<>();
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int start = hasProblemHeader(lines) ? 1 : 0;
        for (int index = start; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCells(line);
            CreateProblemRequest request = new CreateProblemRequest();
            request.setTitle(get(cells, 0));
            request.setDescription(get(cells, 1));
            request.setDifficulty(parseDifficulty(get(cells, 2)));
            request.setTimeLimit(parseInt(get(cells, 3), 1000));
            request.setMemoryLimit(parseInt(get(cells, 4), 128000));
            request.setAiPromptDirection(get(cells, 5));
            CreateProblemRequest.TestCaseRequest sample = new CreateProblemRequest.TestCaseRequest();
            sample.setInput(get(cells, 6));
            sample.setExpectedOutput(get(cells, 7));
            sample.setHidden(false);
            request.setTestCases(sample.getInput().isBlank() && sample.getExpectedOutput().isBlank() ? List.of() : List.of(sample));
            rows.add(toProblemRow(index + 1, request, issues));
        }
        return markProblemDuplicates(rows);
    }

    private ImportPreviewResponse.ProblemImportRow toProblemRow(int rowNumber,
                                                                CreateProblemRequest request,
                                                                List<ImportPreviewResponse.RowIssue> issues) {
        List<String> errors = new ArrayList<>();
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            errors.add("缺少题目标题");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            errors.add("缺少题目描述");
        }
        if (request.getDifficulty() == null) {
            errors.add("缺少难度");
        }
        if (request.getTimeLimit() == null || request.getTimeLimit() < 100) {
            errors.add("时间限制不能小于 100 ms");
        }
        if (request.getMemoryLimit() == null || request.getMemoryLimit() < 1024) {
            errors.add("内存限制不能小于 1024 KB");
        }
        int testCaseCount = request.getTestCases() == null ? 0 : request.getTestCases().size();
        int visibleCount = request.getTestCases() == null ? 0 : (int) request.getTestCases().stream()
                .filter(testCase -> !Boolean.TRUE.equals(testCase.getHidden()))
                .count();
        if (visibleCount == 0) {
            errors.add("至少需要一个可见测试点");
        }
        errors.forEach(error -> issues.add(issue(rowNumber, "error", error)));

        return ImportPreviewResponse.ProblemImportRow.builder()
                .rowNumber(rowNumber)
                .title(nullToBlank(request.getTitle()))
                .description(nullToBlank(request.getDescription()))
                .difficulty(request.getDifficulty() == null ? "" : request.getDifficulty().name())
                .timeLimit(request.getTimeLimit())
                .memoryLimit(request.getMemoryLimit())
                .aiPromptDirection(nullToBlank(request.getAiPromptDirection()))
                .testCaseCount(testCaseCount)
                .visibleTestCaseCount(visibleCount)
                .valid(errors.isEmpty())
                .duplicate(problemRepository.findAllByOrderByIdAsc().stream()
                        .anyMatch(problem -> problem.getTitle().equalsIgnoreCase(nullToBlank(request.getTitle()).trim())))
                .message(String.join("；", errors))
                .payloadJson(toJson(request))
                .build();
    }

    private List<ImportPreviewResponse.ProblemImportRow> markProblemDuplicates(List<ImportPreviewResponse.ProblemImportRow> rows) {
        Set<String> seen = new LinkedHashSet<>();
        List<ImportPreviewResponse.ProblemImportRow> normalized = new ArrayList<>();
        for (ImportPreviewResponse.ProblemImportRow row : rows) {
            String key = row.getTitle().trim().toLowerCase(Locale.ROOT);
            boolean duplicate = row.isDuplicate() || !seen.add(key);
            normalized.add(ImportPreviewResponse.ProblemImportRow.builder()
                    .rowNumber(row.getRowNumber())
                    .title(row.getTitle())
                    .description(row.getDescription())
                    .difficulty(row.getDifficulty())
                    .timeLimit(row.getTimeLimit())
                    .memoryLimit(row.getMemoryLimit())
                    .aiPromptDirection(row.getAiPromptDirection())
                    .testCaseCount(row.getTestCaseCount())
                    .visibleTestCaseCount(row.getVisibleTestCaseCount())
                    .valid(row.isValid())
                    .duplicate(duplicate)
                    .message(duplicate && row.getMessage().isBlank() ? "题目重复" : row.getMessage())
                    .payloadJson(row.getPayloadJson())
                    .build());
        }
        return normalized;
    }

    private List<CreateProblemRequest.TestCaseRequest> extractMarkdownSamples(String content) {
        List<String> codeBlocks = extractCodeBlocks(content);
        if (codeBlocks.size() >= 2) {
            CreateProblemRequest.TestCaseRequest testCase = new CreateProblemRequest.TestCaseRequest();
            testCase.setInput(codeBlocks.get(0));
            testCase.setExpectedOutput(codeBlocks.get(1));
            testCase.setHidden(false);
            return List.of(testCase);
        }

        String lower = content.toLowerCase(Locale.ROOT);
        int inputIndex = firstIndexOf(lower, "输入", "input");
        int outputIndex = firstIndexOf(lower, "输出", "output");
        if (inputIndex < 0 || outputIndex < 0 || outputIndex <= inputIndex) {
            return List.of();
        }
        String input = extractFirstCodeBlock(content.substring(inputIndex, outputIndex));
        String output = extractFirstCodeBlock(content.substring(outputIndex));
        if (input.isBlank() || output.isBlank()) {
            return List.of();
        }
        CreateProblemRequest.TestCaseRequest testCase = new CreateProblemRequest.TestCaseRequest();
        testCase.setInput(input);
        testCase.setExpectedOutput(output);
        testCase.setHidden(false);
        return List.of(testCase);
    }

    private List<String> extractCodeBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        int cursor = 0;
        while (cursor >= 0 && cursor < content.length()) {
            int start = content.indexOf("```", cursor);
            if (start < 0) {
                break;
            }
            int contentStart = content.indexOf('\n', start);
            int end = content.indexOf("```", contentStart + 1);
            if (contentStart < 0 || end < 0) {
                break;
            }
            blocks.add(content.substring(contentStart + 1, end).trim());
            cursor = end + 3;
        }
        return blocks;
    }

    private String extractFirstCodeBlock(String text) {
        int start = text.indexOf("```");
        if (start < 0) {
            return "";
        }
        int contentStart = text.indexOf('\n', start);
        int end = text.indexOf("```", contentStart + 1);
        if (contentStart < 0 || end < 0) {
            return "";
        }
        return text.substring(contentStart + 1, end).trim();
    }

    private String extractMarkdownTitle(String content) {
        for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+", "").trim();
            }
        }
        String first = content.strip().split("\n")[0].trim();
        return first.length() > 40 ? first.substring(0, 40) : first;
    }

    private ClassGroup resolveClassGroup(Long classGroupId, String className) {
        if (classGroupId == null) throw new IllegalArgumentException("名单导入必须选择班级");
        java.util.UUID teacherId = currentTeacherContext == null
                ? TeacherAccount.BOOTSTRAP_ADMIN_ID : currentTeacherContext.requireTeacherId();
        return classGroupRepository.findByIdAndOwnerTeacherId(classGroupId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在: " + classGroupId));
    }

    private boolean hasStudentHeader(String[] lines) {
        if (lines.length == 0) {
            return false;
        }
        String header = lines[0].toLowerCase(Locale.ROOT);
        return header.contains("姓名")
                || header.contains("name")
                || header.contains("displayname")
                || header.contains("student");
    }

    private boolean hasProblemHeader(String[] lines) {
        if (lines.length == 0) {
            return false;
        }
        String header = lines[0].toLowerCase(Locale.ROOT);
        return header.contains("title")
                || header.contains("题目")
                || header.contains("任务标题")
                || header.contains("description");
    }

    private List<String> splitCells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && (ch == ',' || ch == '\t' || ch == '，')) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private String normalizeContent(ImportRequest request) {
        String content = request == null ? "" : nullToBlank(request.getContent());
        if (content.startsWith("data:")) {
            int comma = content.indexOf(',');
            content = comma >= 0 ? content.substring(comma + 1) : content;
        }
        if (content.matches("^[A-Za-z0-9+/=\\r\\n]+$") && content.length() > 80 && !content.contains(",") && !content.contains("{")) {
            try {
                return new String(Base64.getDecoder().decode(content.replaceAll("\\s+", "")), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return content;
            }
        }
        return content;
    }

    private byte[] normalizeBinaryContent(ImportRequest request) {
        String content = request == null ? "" : nullToBlank(request.getContent());
        if (content.startsWith("data:")) {
            int comma = content.indexOf(',');
            content = comma >= 0 ? content.substring(comma + 1) : content;
        }
        try {
            return Base64.getDecoder().decode(content.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException ignored) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String extractXlsxAsDelimitedText(ImportRequest request, List<ImportPreviewResponse.RowIssue> issues) {
        try {
            byte[] bytes = normalizeBinaryContent(request);
            String sharedStringsXml = null;
            String sheetXml = null;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if ("xl/sharedStrings.xml".equals(entry.getName())) {
                        sharedStringsXml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                        sheetXml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            if (sheetXml == null || sheetXml.isBlank()) {
                issues.add(issue(1, "error", "XLSX 中未找到第一个工作表。"));
                return "";
            }
            List<String> sharedStrings = parseSharedStrings(sharedStringsXml);
            Document document = parseXml(sheetXml);
            NodeList rows = document.getElementsByTagName("row");
            List<String> lines = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rows.getLength(); rowIndex++) {
                NodeList cells = rows.item(rowIndex).getChildNodes();
                List<String> values = new ArrayList<>();
                for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                    if (!"c".equals(cells.item(cellIndex).getNodeName())) {
                        continue;
                    }
                    String type = cells.item(cellIndex).getAttributes().getNamedItem("t") == null
                            ? ""
                            : cells.item(cellIndex).getAttributes().getNamedItem("t").getNodeValue();
                    NodeList children = cells.item(cellIndex).getChildNodes();
                    String value = "";
                    for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                        if ("v".equals(children.item(childIndex).getNodeName())) {
                            value = children.item(childIndex).getTextContent();
                            break;
                        }
                    }
                    if ("s".equals(type)) {
                        int sharedIndex = parseInt(value, -1);
                        value = sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
                    }
                    values.add(value);
                }
                if (!values.isEmpty()) {
                    lines.add(String.join(",", values));
                }
            }
            return String.join("\n", lines);
        } catch (Exception exception) {
            issues.add(issue(1, "error", "XLSX 解析失败：" + exception.getMessage()));
            return "";
        }
    }

    private List<String> parseSharedStrings(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        Document document = parseXml(xml);
        NodeList items = document.getElementsByTagName("si");
        List<String> values = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            values.add(items.item(index).getTextContent());
        }
        return values;
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String normalizeFormat(ImportRequest request, String content) {
        String format = request == null ? "" : nullToBlank(request.getFormat()).toLowerCase(Locale.ROOT);
        String fileName = request == null ? "" : nullToBlank(request.getFileName()).toLowerCase(Locale.ROOT);
        if (!format.isBlank()) {
            return format;
        }
        if (fileName.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (fileName.endsWith(".json")) {
            return "json";
        }
        if (fileName.endsWith(".md") || fileName.endsWith(".markdown")) {
            return "markdown";
        }
        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            return "json";
        }
        return "csv";
    }

    private Problem.Difficulty parseDifficulty(String value) {
        String normalized = nullToBlank(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MEDIUM", "中等", "中" -> Problem.Difficulty.MEDIUM;
            case "HARD", "困难", "难" -> Problem.Difficulty.HARD;
            default -> Problem.Difficulty.EASY;
        };
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(nullToBlank(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String get(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? nullToBlank(cells.get(index)) : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int firstIndexOf(String text, String... needles) {
        int result = -1;
        for (String needle : needles) {
            int index = text.indexOf(needle.toLowerCase(Locale.ROOT));
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private ImportPreviewResponse.RowIssue issue(int rowNumber, String severity, String message) {
        return ImportPreviewResponse.RowIssue.builder()
                .rowNumber(rowNumber)
                .severity(severity)
                .message(message)
                .build();
    }
}
