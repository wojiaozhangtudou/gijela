package com.gijela.morpheus.chat.adapter.extractor;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件内容提取器。
 * 优先使用 Apache Tika 提取多格式文本；失败时回退到文本启发式识别。
 */
@Component
public class FileContentExtractor {

    private final Tika tika = new Tika();

    /**
     * 从文件字节中提取纯文本内容。
     *
     * @param filename 原始文件名（含扩展名）
     * @param contentType 上传时的 contentType
     * @param content  文件字节
     * @param maxChars 最大字符数，超出截断
     * @return 提取的文本，不支持的类型返回 null
     */
    public String extract(String filename, String contentType, byte[] content, int maxChars) {
        if (content == null || content.length == 0) {
            return null;
        }

        int effectiveMax = maxChars > 0 ? maxChars : 100_000;
        tika.setMaxStringLength(effectiveMax);

        // 1) 优先走 Tika，多格式统一提取（pdf/doc/docx/xlsx/pptx/rtf/html/txt...）
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            String parsed = tika.parseToString(inputStream);
            if (parsed != null && !parsed.isBlank()) {
                return trimByMaxChars(parsed, effectiveMax);
            }
        } catch (IOException ignored) {
            // 回退到轻量文本启发式
        } catch (Exception ignored) {
            // 包含 TikaException 等解析异常，回退到轻量文本启发式
        }

        boolean suffixText = false;
        if (filename != null) {
            String lower = filename.toLowerCase();
            suffixText = lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
        }

        boolean mimeText = false;
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            mimeText = ct.startsWith("text/") || ct.contains("json") || ct.contains("xml") || ct.contains("yaml") || ct.contains("csv");
        }

        String text = new String(content, StandardCharsets.UTF_8);
        if (suffixText || mimeText || looksLikePlainText(text)) {
            return trimByMaxChars(text, effectiveMax);
        }

        // 非文本或无法提取
        return null;
    }

    private String trimByMaxChars(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars > 0 && text.length() > maxChars) {
            return text.substring(0, maxChars);
        }
        return text;
    }

    private boolean looksLikePlainText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        int len = text.length();
        int replacement = 0;
        int control = 0;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') {
                replacement++;
            }
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                control++;
            }
        }
        double replacementRatio = (double) replacement / len;
        double controlRatio = (double) control / len;
        return replacementRatio < 0.02 && controlRatio < 0.05;
    }
}
