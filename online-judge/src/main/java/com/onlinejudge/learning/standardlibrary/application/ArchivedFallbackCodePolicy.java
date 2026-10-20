package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;

import java.util.Locale;
import java.util.regex.Pattern;

/** 识别已经退役的历史全覆盖条目，不依赖任何 seed catalog。 */
final class ArchivedFallbackCodePolicy {

    private static final Pattern GENERATED_CODE = Pattern.compile("^(SK|MP|KB)(?:_[A-Z0-9]+)*_[0-9A-F]{1,8}$");

    private ArchivedFallbackCodePolicy() {
    }

    static boolean isArchivedFallback(AiStandardLibraryLayer layer, String code) {
        if (layer != AiStandardLibraryLayer.SKILL_UNIT
                && layer != AiStandardLibraryLayer.MISTAKE_POINT
                && layer != AiStandardLibraryLayer.BASIC_CAUSE) {
            return false;
        }
        return isArchivedFallback(code);
    }

    static boolean isArchivedFallback(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return !normalized.isBlank() && GENERATED_CODE.matcher(normalized).matches();
    }
}
