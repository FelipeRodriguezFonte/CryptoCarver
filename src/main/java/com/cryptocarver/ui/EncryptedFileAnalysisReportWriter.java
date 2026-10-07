package com.cryptocarver.ui;

import com.cryptocarver.ui.EncryptedFileAnalyzer.AnalysisCandidate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Formats analysis candidates as text and HTML without evaluating or ranking them. */
final class EncryptedFileAnalysisReportWriter {
    private EncryptedFileAnalysisReportWriter() { }

    static String formatAnalysisReport(Path inputFile,
            int originalFileSize,
            int analyzedBytes,
            boolean sampled,
            int attempts,
            int successes,
            List<AnalysisCandidate> topCandidates,
            List<AnalysisCandidate> probableCandidates,
            Path analysisDirectory,
            Path attemptsLogPath) {
        StringBuilder report = new StringBuilder();
        report.append("=== ENCRYPTED FILE ANALYSIS REPORT ===\n\n");
        report.append("File: ").append(inputFile).append("\n");
        report.append("File size: ").append(originalFileSize).append(" bytes\n");
        report.append("Analyzed bytes: ").append(analyzedBytes).append(" bytes");
        if (sampled) {
            report.append(" (sampled)");
        }
        report.append("\n");
        report.append("Total attempts: ").append(attempts).append("\n");
        report.append("Successful decryptions: ").append(successes).append("\n\n");
        report.append("Analysis Directory: ").append(analysisDirectory).append("\n");
        report.append("Attempt Log: ").append(attemptsLogPath).append("\n\n");

        AnalysisCandidate best = topCandidates.get(0);
        report.append("Best candidate:\n");
        report.append("Algorithm: ").append(best.algorithm).append("\n");
        report.append("Mode: ").append(best.mode).append("\n");
        report.append("Padding: ").append(best.padding).append("\n");
        report.append("Processing: ").append(best.processing).append("\n");
        if (best.blockSize > 0) {
            report.append("Block size: ").append(best.blockSize).append(" bytes\n");
        }
        report.append("Encrypted file input encoding: ").append(best.inputEncoding).append("\n");
        report.append("Inferred plaintext encoding: ").append(best.inferredPlainEncoding).append("\n");
        report.append("Score: ").append(best.score).append("\n");
        report.append("Confidence: ").append(EncryptedFileAnalyzer.formatPercent(best.confidencePercent)).append("\n");
        report.append("Score breakdown: base=").append(best.baseScore)
                .append(" + paddingAdj=").append(best.paddingAdjustment)
                .append(" => ").append(best.score).append("\n");
        report.append("Quality: ").append(best.qualitySummary).append("\n");
        report.append("Preview: ").append(best.preview).append("\n\n");

        report.append("Most probable candidates:\n");
        int probableRank = 1;
        for (AnalysisCandidate candidate : probableCandidates) {
            report.append(probableRank).append(". ");
            report.append(candidate.algorithm).append("/").append(candidate.mode).append("/").append(candidate.padding);
            report.append(" | ").append(candidate.processing);
            if (candidate.blockSize > 0) {
                report.append(" (block=").append(candidate.blockSize).append(" bytes)");
            }
            report.append(" | enc=").append(candidate.inputEncoding);
            report.append(" | plain=").append(candidate.inferredPlainEncoding);
            report.append(" | score=").append(candidate.score)
                    .append(" (base=").append(candidate.baseScore)
                    .append(", padAdj=").append(candidate.paddingAdjustment)
                    .append(")")
                    .append(" | conf=").append(EncryptedFileAnalyzer.formatPercent(candidate.confidencePercent))
                    .append("\n");
            report.append("   preview: ").append(candidate.preview).append("\n");
            probableRank++;
        }
        report.append("\n");

        report.append("Top candidates:\n");
        int rank = 1;
        for (AnalysisCandidate candidate : topCandidates) {
            report.append(rank).append(". ");
            report.append(candidate.algorithm).append("/").append(candidate.mode).append("/").append(candidate.padding);
            report.append(" | ").append(candidate.processing);
            if (candidate.blockSize > 0) {
                report.append(" (block=").append(candidate.blockSize).append(" bytes)");
            }
            report.append(" | enc=").append(candidate.inputEncoding);
            report.append(" | plain=").append(candidate.inferredPlainEncoding);
            report.append(" | score=").append(candidate.score)
                    .append(" (base=").append(candidate.baseScore)
                    .append(", padAdj=").append(candidate.paddingAdjustment)
                    .append(")")
                    .append(" | conf=").append(EncryptedFileAnalyzer.formatPercent(candidate.confidencePercent))
                    .append("\n");
            report.append("   quality: ").append(candidate.qualitySummary).append("\n");
            report.append("   preview: ").append(candidate.preview).append("\n");
            rank++;
        }

        report.append("\nNotes:\n");
        report.append("- INDEPENDENT_BLOCKS_STRUCTURED means CryptoCarver expert block container matched.\n");
        report.append("- INDEPENDENT_BLOCKS_GUESS means heuristic split-by-size decryption.\n");
        report.append("- Score formula: final = base plaintext quality + padding adjustment.\n");
        report.append("- Confidence % is a softmax normalization over displayed top candidates (temperature=6).\n");
        report.append("- Confidence % is comparative, not an absolute proof of correctness.\n");
        report.append("- PKCS5Padding and PKCS7Padding are equivalent in this Java/provider setup and are grouped.\n");
        report.append("- Candidate chunk sizes in options are interpreted as BYTES by default.\n");
        report.append("- Chunk size is file splitting size for independent processing, not cipher primitive block size.\n");
        report.append("- Ranking is heuristic and should be validated with domain context.\n");
        return report.toString();
    }

    static void writeHtmlReport(Path htmlPath,
            Path inputFile,
            int originalFileSize,
            int analyzedBytes,
            boolean sampled,
            int attempts,
            int successes,
            List<AnalysisCandidate> topCandidates,
            List<AnalysisCandidate> probableCandidates,
            Path attemptsLogPath) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        html.append("<title>CryptoCarver Analysis Report</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;background:#f8fafc;color:#0f172a;}");
        html.append("h1,h2{margin:0 0 12px 0;} .card{background:#fff;border:1px solid #e2e8f0;border-radius:10px;padding:16px;margin-bottom:16px;}");
        html.append("table{width:100%;border-collapse:collapse;} th,td{border:1px solid #e2e8f0;padding:8px;vertical-align:top;text-align:left;}");
        html.append("th{background:#f1f5f9;} .probable{border:2px solid #16a34a;} code{background:#f1f5f9;padding:2px 4px;border-radius:4px;}");
        html.append("</style></head><body>");
        html.append("<h1>Encrypted File Analysis Report</h1>");

        html.append("<div class=\"card\">");
        html.append("<p><strong>File:</strong> ").append(htmlEscape(String.valueOf(inputFile))).append("</p>");
        html.append("<p><strong>File size:</strong> ").append(originalFileSize).append(" bytes</p>");
        html.append("<p><strong>Analyzed bytes:</strong> ").append(analyzedBytes).append(" bytes");
        if (sampled) {
            html.append(" (sampled)");
        }
        html.append("</p>");
        html.append("<p><strong>Total attempts:</strong> ").append(attempts).append("</p>");
        html.append("<p><strong>Successful decryptions:</strong> ").append(successes).append("</p>");
        html.append("<p><strong>Attempt log CSV:</strong> ").append(htmlEscape(String.valueOf(attemptsLogPath))).append("</p>");
        html.append("</div>");

        if (topCandidates.isEmpty()) {
            html.append("<div class=\"card\"><h2>No candidates found</h2><p>No valid decryption candidates were produced.</p></div>");
        } else {
            AnalysisCandidate best = topCandidates.get(0);
            html.append("<div class=\"card probable\">");
            html.append("<h2>Most probable result</h2>");
            html.append("<p><strong>Algorithm:</strong> ").append(htmlEscape(best.algorithm)).append("</p>");
            html.append("<p><strong>Mode/Padding:</strong> ").append(htmlEscape(best.mode)).append(" / ")
                    .append(htmlEscape(best.padding)).append("</p>");
            html.append("<p><strong>Processing:</strong> ").append(htmlEscape(best.processing)).append("</p>");
            if (best.blockSize > 0) {
                html.append("<p><strong>Block size:</strong> ").append(best.blockSize).append(" bytes</p>");
            }
            html.append("<p><strong>Input encoding:</strong> ").append(htmlEscape(best.inputEncoding.name())).append("</p>");
            html.append("<p><strong>Inferred plaintext encoding:</strong> ").append(htmlEscape(best.inferredPlainEncoding))
                    .append("</p>");
            html.append("<p><strong>Score:</strong> ").append(best.score).append("</p>");
            html.append("<p><strong>Confidence:</strong> ").append(EncryptedFileAnalyzer.formatPercent(best.confidencePercent)).append("</p>");
            html.append("<p><strong>Score breakdown:</strong> base=").append(best.baseScore)
                    .append(" + paddingAdj=").append(best.paddingAdjustment)
                    .append(" =&gt; ").append(best.score).append("</p>");
            html.append("<p><strong>Quality:</strong> ").append(htmlEscape(best.qualitySummary)).append("</p>");
            html.append("<p><strong>Preview:</strong> <code>").append(htmlEscape(best.preview)).append("</code></p>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Most probable candidates</h2>");
            html.append("<table><thead><tr><th>#</th><th>Candidate</th><th>Processing</th><th>Input Enc.</th><th>Plain Enc.</th><th>Score</th><th>Confidence</th><th>Preview</th></tr></thead><tbody>");
            int i = 1;
            for (AnalysisCandidate candidate : probableCandidates) {
                html.append("<tr>");
                html.append("<td>").append(i++).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.algorithm + "/" + candidate.mode + "/" + candidate.padding))
                        .append("</td>");
                html.append("<td>").append(htmlEscape(candidate.processing));
                if (candidate.blockSize > 0) {
                    html.append(" (block=").append(candidate.blockSize).append(" bytes)");
                }
                html.append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inputEncoding.name())).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inferredPlainEncoding)).append("</td>");
                html.append("<td>").append(candidate.score)
                        .append(" (").append(candidate.baseScore)
                        .append(" + ").append(candidate.paddingAdjustment)
                        .append(")</td>");
                html.append("<td>").append(EncryptedFileAnalyzer.formatPercent(candidate.confidencePercent)).append("</td>");
                html.append("<td><code>").append(htmlEscape(candidate.preview)).append("</code></td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Top candidates</h2>");
            html.append("<table><thead><tr><th>#</th><th>Candidate</th><th>Processing</th><th>Input Enc.</th><th>Plain Enc.</th><th>Score</th><th>Confidence</th><th>Quality</th><th>Preview</th></tr></thead><tbody>");
            int rank = 1;
            for (AnalysisCandidate candidate : topCandidates) {
                html.append("<tr>");
                html.append("<td>").append(rank++).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.algorithm + "/" + candidate.mode + "/" + candidate.padding))
                        .append("</td>");
                html.append("<td>").append(htmlEscape(candidate.processing));
                if (candidate.blockSize > 0) {
                    html.append(" (block=").append(candidate.blockSize).append(" bytes)");
                }
                html.append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inputEncoding.name())).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inferredPlainEncoding)).append("</td>");
                html.append("<td>").append(candidate.score)
                        .append(" (").append(candidate.baseScore)
                        .append(" + ").append(candidate.paddingAdjustment)
                        .append(")</td>");
                html.append("<td>").append(EncryptedFileAnalyzer.formatPercent(candidate.confidencePercent)).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.qualitySummary)).append("</td>");
                html.append("<td><code>").append(htmlEscape(candidate.preview)).append("</code></td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Scoring notes</h2>");
            html.append("<ul>");
            html.append("<li>Final score = base plaintext quality + padding adjustment.</li>");
            html.append("<li>Confidence % is a softmax normalization over displayed top candidates (temperature=6).</li>");
            html.append("<li>Confidence % is comparative, not an absolute proof of correctness.</li>");
            html.append("<li>PKCS5Padding and PKCS7Padding are equivalent in this Java/provider setup and are grouped.</li>");
            html.append("<li>Candidate chunk sizes are interpreted as bytes by default in the analyzer dialog.</li>");
            html.append("<li>Chunk size is file splitting size for independent processing, not cipher primitive block size.</li>");
            html.append("</ul>");
            html.append("</div>");
        }

        html.append("</body></html>");
        Files.writeString(htmlPath, html.toString(), StandardCharsets.UTF_8);
    }

    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

}
