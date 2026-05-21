package com.gijela.morpheus.chat.llm.log.es;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class EsDesensitizer {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6}(?:\\d{8}|\\d{12}[0-9Xx]))(?!\\d)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(sk-[A-Za-z0-9_-]{8,}|bearer\\s+[A-Za-z0-9._-]{8,}|apikey\\s*[:=]\\s*[A-Za-z0-9_-]{8,})");

    public Map<String, Object> desensitize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> target = new LinkedHashMap<>(source);
        target.remove("promptRaw");
        target.remove("contentRaw");
        target.remove("attachmentRaw");
        target.computeIfPresent("promptPreview", (key, value) -> maskText(String.valueOf(value)));
        target.computeIfPresent("errorMsg", (key, value) -> maskText(String.valueOf(value)));
        target.computeIfPresent("apiKey", (key, value) -> desensitizeApiKey(String.valueOf(value)));
        return target;
    }

    public String desensitizePhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public String desensitizeIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "********" + idCard.substring(idCard.length() - 4);
    }

    public String desensitizeApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey) || apiKey.length() < 8) {
            return "[REDACTED]";
        }
        return apiKey.substring(0, Math.min(4, apiKey.length())) + "****";
    }

    private String maskText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String afterPhone = PHONE_PATTERN.matcher(raw).replaceAll(match -> desensitizePhone(match.group(1)));
        String afterIdCard = ID_CARD_PATTERN.matcher(afterPhone).replaceAll(match -> desensitizeIdCard(match.group(1)));
        return API_KEY_PATTERN.matcher(afterIdCard).replaceAll("[REDACTED]");
    }
}
