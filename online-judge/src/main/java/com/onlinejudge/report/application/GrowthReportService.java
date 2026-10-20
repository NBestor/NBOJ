package com.onlinejudge.report.application;

import com.onlinejudge.report.dto.GrowthReportResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.application.ProblemAccessPolicy;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.submission.application.AiReportService;
import com.onlinejudge.submission.application.SubmissionAnalysisService;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrowthReportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final AiReportService aiReportService;
    private final AssignmentRepository assignmentRepository;
    private final CurrentTeacherContext currentTeacherContext;
    private final ProblemAccessPolicy problemAccessPolicy;

    public GrowthReportResponse buildGrowthReport(Long problemId) {
        UUID teacherId = currentTeacherContext.requireTeacherId();
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));
        if (!problemAccessPolicy.isTeacherVisible(teacherId, problem)) {
            throw new com.onlinejudge.shared.web.PlatformApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在");
        }
        Set<Long> ownedAssignmentIds = assignmentRepository.findByOwnerTeacherIdOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(assignment -> assignment.getId())
                .collect(Collectors.toSet());

        List<SubmissionResponse> submissions = submissionRepository.findByProblemIdOrderBySubmittedAtAsc(problemId)
                .stream()
                .filter(submission -> submission.getAssignmentId() != null
                        && ownedAssignmentIds.contains(submission.getAssignmentId()))
                .map(submission -> submissionAnalysisService.getDetailedSubmission(submission.getId()))
                .toList();

        List<GrowthReportResponse.Milestone> milestones = submissions.stream()
                .map(this::toMilestone)
                .toList();

        int acceptedCount = (int) submissions.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .count();

        String markdown = aiReportService.enhanceGrowthReportMarkdown(problem, buildAiTimeline(submissions));

        return GrowthReportResponse.builder()
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .submissionCount(submissions.size())
                .acceptedCount(acceptedCount)
                .generatedAt(LocalDateTime.now())
                .markdown(markdown)
                .milestones(milestones)
                .build();
    }

    public byte[] exportGrowthReport(Long problemId, String format) {
        GrowthReportResponse report = buildGrowthReport(problemId);
        String normalizedFormat = format == null ? "markdown" : format.trim().toLowerCase();

        return switch (normalizedFormat) {
            case "markdown", "md" -> report.getMarkdown().getBytes(StandardCharsets.UTF_8);
            case "pdf" -> renderPdf(report.getMarkdown());
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    private GrowthReportResponse.Milestone toMilestone(SubmissionResponse submission) {
        return GrowthReportResponse.Milestone.builder()
                .submissionId(submission.getId())
                .verdict(submissionAnalysisService.formatVerdict(submission.getVerdict()))
                .submittedAt(submission.getSubmittedAt())
                .summary(submission.getAnalysis() == null ? "" : submission.getAnalysis().getSummary())
                .build();
    }

    private List<Map<String, Object>> buildAiTimeline(List<SubmissionResponse> submissions) {
        return submissions.stream()
                .map(submission -> Map.<String, Object>of(
                        "submissionId", submission.getId(),
                        "submittedAt", formatTime(submission.getSubmittedAt()),
                        "verdict", submissionAnalysisService.formatVerdict(submission.getVerdict()),
                        "summary", submission.getAnalysis() == null ? "" : submission.getAnalysis().getSummary(),
                        "headline", submission.getAnalysis() == null ? "" : submission.getAnalysis().getHeadline()
                ))
                .toList();
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : TIME_FORMATTER.format(time);
    }

    private byte[] renderPdf(String markdown) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            float fontSize = 11f;
            float lineHeight = 16f;
            float margin = 48f;
            float contentWidth = PDRectangle.A4.getWidth() - margin * 2;
            String sanitizedMarkdown = sanitizeForPdf(markdown, font);
            List<String> wrappedLines = wrapMarkdown(sanitizedMarkdown, font, fontSize, contentWidth);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.setLeading(lineHeight);
            float currentY = PDRectangle.A4.getHeight() - margin;
            stream.newLineAtOffset(margin, currentY);

            for (String line : wrappedLines) {
                if (currentY - lineHeight < margin) {
                    stream.endText();
                    stream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.setLeading(lineHeight);
                    currentY = PDRectangle.A4.getHeight() - margin;
                    stream.newLineAtOffset(margin, currentY);
                }

                stream.showText(line.isEmpty() ? " " : line);
                stream.newLineAtOffset(0, -lineHeight);
                currentY -= lineHeight;
            }

            stream.endText();
            stream.close();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("生成成长报告 PDF 失败: " + exception.getMessage(), exception);
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        String windir = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
        List<Path> candidates = List.of(
                Path.of(windir, "Fonts", "NotoSansSC-VF.ttf"),
                Path.of(windir, "Fonts", "simhei.ttf"),
                Path.of(windir, "Fonts", "msyh.ttf")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return PDType0Font.load(document, candidate.toFile());
            }
        }

        throw new IllegalStateException("未找到可用于 PDF 导出的中文字体");
    }

    private List<String> wrapMarkdown(String markdown, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace("\r", "\n");
        String[] rawLines = normalized.split("\n", -1);

        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (int index = 0; index < rawLine.length(); index++) {
                char currentChar = rawLine.charAt(index);
                String candidate = current + String.valueOf(currentChar);
                float width = font.getStringWidth(candidate) / 1000 * fontSize;
                if (width > maxWidth && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(currentChar);
            }
            lines.add(current.toString());
        }

        return lines;
    }

    private String sanitizeForPdf(String markdown, PDFont font) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder sanitized = new StringBuilder();

        for (int index = 0; index < normalized.length(); ) {
            int codePoint = normalized.codePointAt(index);
            index += Character.charCount(codePoint);

            if (codePoint == '\n') {
                sanitized.append('\n');
                continue;
            }

            String current = new String(Character.toChars(codePoint));
            if (isPdfGlyphSupported(font, current)) {
                sanitized.append(current);
                continue;
            }

            String replacement = replacementForCodePoint(codePoint);
            if (!replacement.isEmpty() && isPdfGlyphSupported(font, replacement)) {
                sanitized.append(replacement);
                continue;
            }

            sanitized.append(Character.isWhitespace(codePoint) ? ' ' : '?');
        }

        return sanitized.toString();
    }

    private boolean isPdfGlyphSupported(PDFont font, String text) {
        try {
            font.getStringWidth(text);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private String replacementForCodePoint(int codePoint) {
        return switch (codePoint) {
            case 0x2705 -> "[通过]";
            case 0x274C -> "[失败]";
            case 0x26A0 -> "[注意]";
            case 0x1F4CC -> "[重点]";
            case 0x1F9E0 -> "[分析]";
            case 0x1F680 -> "[优化]";
            case 0x2728 -> "[亮点]";
            case 0x1F50D -> "[排查]";
            case 0x1F4C8 -> "[提升]";
            case 0x1F4C9 -> "[回退]";
            case 0x1F3AF -> "[目标]";
            case 0x1F4DD -> "[记录]";
            case 0x1F4C4 -> "[文档]";
            case 0x1F4A1 -> "[建议]";
            default -> "";
        };
    }
}
