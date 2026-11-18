package com.ketabkhan.build;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure JVM validator for Base64-encoded Vazirmatn SFNT (.ttf / .otf / .ttc) font assets.
 * Shared between the Gradle `decodeVazirmatnFonts` task and unit tests.
 */
public final class VazirmatnFontValidator {

    public static final int MIN_FONT_SIZE_BYTES = 20 * 1024;
    public static final int MIN_SFNT_TABLES = 4;
    public static final int MAX_SFNT_TABLES = 256;
    public static final List<String> REQUIRED_FONT_BASES = List.of(
        "vazirmatn_regular",
        "vazirmatn_medium",
        "vazirmatn_bold"
    );

    private VazirmatnFontValidator() {}

    public static final class FontValidationException extends RuntimeException {
        private final String fileName;
        private final String issueType;
        private final String maintainerAction;

        public FontValidationException(String fileName, String issueType, String maintainerAction) {
            super(formatMessage(fileName, issueType, maintainerAction));
            this.fileName = fileName;
            this.issueType = issueType;
            this.maintainerAction = maintainerAction;
        }

        public FontValidationException(
            String fileName,
            String issueType,
            String maintainerAction,
            Throwable cause
        ) {
            super(formatMessage(fileName, issueType, maintainerAction), cause);
            this.fileName = fileName;
            this.issueType = issueType;
            this.maintainerAction = maintainerAction;
        }

        public String getFileName() {
            return fileName;
        }

        public String getIssueType() {
            return issueType;
        }

        public String getMaintainerAction() {
            return maintainerAction;
        }

        private static String formatMessage(
            String fileName,
            String issueType,
            String maintainerAction
        ) {
            return "خطا در اعتبارسنجی فونت | فایل مشکل‌دار: " + fileName +
                " | نوع مشکل: " + issueType +
                " | اقدام لازم maintainer: " + maintainerAction;
        }
    }

    public static final class ValidatedFontInfo {
        private final int sizeBytes;
        private final String signatureLabel;
        private final int tableCount;
        private final Set<String> tableTags;

        public ValidatedFontInfo(
            int sizeBytes,
            String signatureLabel,
            int tableCount,
            Set<String> tableTags
        ) {
            this.sizeBytes = sizeBytes;
            this.signatureLabel = signatureLabel;
            this.tableCount = tableCount;
            this.tableTags = Collections.unmodifiableSet(new LinkedHashSet<>(tableTags));
        }

        public int getSizeBytes() {
            return sizeBytes;
        }

        public String getSignatureLabel() {
            return signatureLabel;
        }

        public int getTableCount() {
            return tableCount;
        }

        public Set<String> getTableTags() {
            return tableTags;
        }
    }

    public static final class DecodedFontResult {
        private final byte[] bytes;
        private final ValidatedFontInfo info;

        public DecodedFontResult(byte[] bytes, ValidatedFontInfo info) {
            this.bytes = bytes;
            this.info = info;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public ValidatedFontInfo getInfo() {
            return info;
        }
    }

    /**
     * Strips UTF-8 BOM, zero-width formatting marks, whitespace, and non-printable control chars
     * before strict Base64 validation. Printable non-Base64 characters are preserved so strict
     * Base64 decoding rejects corrupted payloads with an explicit Persian error.
     */
    public static String cleanBase64Input(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(rawContent.length());
        for (int i = 0; i < rawContent.length(); i++) {
            char c = rawContent.charAt(i);
            if (c == '\uFEFF' || c == '\u200B' || c == '\u200C' || c == '\u200D'
                || c == '\u200E' || c == '\u200F') {
                continue;
            }
            if (Character.isWhitespace(c) || c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static byte[] decodeBase64Strict(String rawContent, String fileName) {
        String cleaned = cleanBase64Input(rawContent);
        if (cleaned.isEmpty()) {
            throw new FontValidationException(
                fileName,
                "فایل خالی است یا دادهٔ Base64 معتبری ندارد",
                "فایل رسمی فونت وزیرمتن (.ttf) را با دستور «base64 -w 0» به .b64 تبدیل کرده و در app/fontsrc/ قرار دهید."
            );
        }

        if (cleaned.length() % 4 != 0) {
            throw new FontValidationException(
                fileName,
                "محتوای Base64 نامعتبر است (طول رشته مضرب ۴ نیست)",
                "فایل .b64 را بدون ویرایش دستی و با دستور استاندارد «base64 -w 0» از روی فونت اصلی بازتولید کنید."
            );
        }

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            boolean isBase64Digit =
                (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '+' || c == '/';
            boolean isValidPadding = (c == '=') && (i >= cleaned.length() - 2);
            if (!isBase64Digit && !isValidPadding) {
                throw new FontValidationException(
                    fileName,
                    "محتوای Base64 نامعتبر است (کاراکتر غیرمجاز در موقعیت " + i + ")",
                    "فایل .b64 را از منبع رسمی فونت وزیرمتن با دستور «base64 -w 0» بازتولید کنید و فایل متنی خراب را جایگزین نمایید."
                );
            }
        }

        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new FontValidationException(
                fileName,
                "خطا در دیکد Base64 (" + e.getMessage() + ")",
                "فایل .b64 را با دستور استاندارد «base64 -w 0» از روی فایل اصلی فونت بازتولید کنید.",
                e
            );
        }
    }

    public static ValidatedFontInfo validateFontBytes(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new FontValidationException(
                fileName,
                "اندازهٔ فایل فونت دیکدشده صفر بایت است",
                "فایل رسمی فونت وزیرمتن را دوباره با «base64 -w 0» تبدیل کرده و در app/fontsrc/ قرار دهید."
            );
        }

        if (bytes.length < MIN_FONT_SIZE_BYTES) {
            throw new FontValidationException(
                fileName,
                "حجم فایل فونت نامعتبر یا جعلی است (" + bytes.length + " بایت؛ حداقل مجاز " + MIN_FONT_SIZE_BYTES + " بایت / ۲۰ کیلوبایت)",
                "فونت ساختگی یا ناقص را حذف کرده و فایل رسمی کامل Vazirmatn (.b64) را در app/fontsrc/ بگذارید."
            );
        }

        String signatureLabel = detectSignature(bytes, 0);
        if (signatureLabel == null) {
            String hex = String.format(
                "0x%02X%02X%02X%02X",
                bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF
            );
            throw new FontValidationException(
                fileName,
                "امضای چهار بایت آغازین فونت نامعتبر است (" + hex + "؛ امضاهای مجاز: 0x00010000، true، ttcf، OTTO)",
                "مطمئن شوید فایل اصلی یک فایل واقعی TTF/OTF/TTC فونت وزیرمتن بوده و به عنوان متن یا تصویر ذخیره نشده است."
            );
        }

        int sfntOffset = 0;
        if ("ttcf".equals(signatureLabel)) {
            if (bytes.length < 16) {
                throw new FontValidationException(
                    fileName,
                    "سرآیند TrueType Collection (ttcf) ناقص است",
                    "فایل .b64 را از فایل رسمی سالم فونت بازتولید کنید."
                );
            }
            long numFonts = readUInt32(bytes, 8);
            if (numFonts < 1 || numFonts > 256 || 12L + numFonts * 4L > bytes.length) {
                throw new FontValidationException(
                    fileName,
                    "تعداد فونت‌های داخل ttcf نامعتبر است (" + numFonts + ")",
                    "فایل رسمی TTF/OTF تک‌وزن Vazirmatn را استفاده کنید."
                );
            }
            long firstOffset = readUInt32(bytes, 12);
            if (firstOffset < 16 || firstOffset + 12L > bytes.length) {
                throw new FontValidationException(
                    fileName,
                    "آفست نخستین فونت داخل ttcf خارج از محدوده فایل است",
                    "فایل رسمی TTF/OTF سالم Vazirmatn را جایگزین کنید."
                );
            }
            sfntOffset = (int) firstOffset;
            String innerSignature = detectSignature(bytes, sfntOffset);
            if (innerSignature == null || "ttcf".equals(innerSignature)) {
                throw new FontValidationException(
                    fileName,
                    "امضای فونت درونی داخل مجموعه ttcf نامعتبر است",
                    "فایل رسمی TTF/OTF سالم Vazirmatn را جایگزین کنید."
                );
            }
        }

        if (sfntOffset + 12 > bytes.length) {
            throw new FontValidationException(
                fileName,
                "سرآیند جدول SFNT ناقص است",
                "فایل فونت را دوباره با «base64 -w 0» تبدیل کرده و جایگزین کنید."
            );
        }

        int numTables = readUInt16(bytes, sfntOffset + 4);
        if (numTables < MIN_SFNT_TABLES || numTables > MAX_SFNT_TABLES) {
            throw new FontValidationException(
                fileName,
                "تعداد جدول‌های فونت نامعقول است (" + numTables + " جدول؛ بازهٔ مجاز ۴ تا ۲۵۶)",
                "فایل واقعی فونت وزیرمتن را از منبع رسمی دریافت کرده و دوباره تبدیل کنید."
            );
        }

        long directoryEnd = sfntOffset + 12L + (long) numTables * 16L;
        if (directoryEnd > bytes.length) {
            throw new FontValidationException(
                fileName,
                "فهرست جدول‌های فونت (Table Directory) ناقص یا بریده است",
                "فایل .b64 ناقص یا بریده شده است؛ فایل کامل را با «base64 -w 0» بازتولید کنید."
            );
        }

        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < numTables; i++) {
            int recordOffset = sfntOffset + 12 + i * 16;
            String tag = new String(bytes, recordOffset, 4, StandardCharsets.US_ASCII);
            long tableOffset = readUInt32(bytes, recordOffset + 8);
            long tableLength = readUInt32(bytes, recordOffset + 12);
            if (tableLength == 0 || tableOffset + tableLength > bytes.length) {
                throw new FontValidationException(
                    fileName,
                    "جدول «" + tag + "» در فونت دارای طول یا آفست نامعتبر است (فایل ناقص)",
                    "فایل کامل و سالم فونت وزیرمتن را دوباره به Base64 تبدیل کنید."
                );
            }
            tags.add(tag);
        }

        boolean hasHead = tags.contains("head");
        boolean hasCmap = tags.contains("cmap");
        boolean hasOutlines = tags.contains("glyf") || tags.contains("CFF ") || tags.contains("CFF2");
        if (!hasHead || !hasCmap || !hasOutlines) {
            StringBuilder missingTables = new StringBuilder();
            if (!hasHead) missingTables.append("head");
            if (!hasCmap) {
                if (missingTables.length() > 0) missingTables.append(", ");
                missingTables.append("cmap");
            }
            if (!hasOutlines) {
                if (missingTables.length() > 0) missingTables.append(", ");
                missingTables.append("glyf/CFF");
            }
            throw new FontValidationException(
                fileName,
                "جدول‌های حیاتی فونت یافت نشدند: " + missingTables,
                "فایل فونت معتبر نیست؛ فایل رسمی Vazirmatn دارای جدول‌های کامل حروف را جایگزین کنید."
            );
        }

        return new ValidatedFontInfo(bytes.length, signatureLabel, numTables, tags);
    }

    public static DecodedFontResult decodeAndValidate(String rawBase64, String sourceFileName) {
        byte[] decoded = decodeBase64Strict(rawBase64, sourceFileName);
        ValidatedFontInfo info = validateFontBytes(decoded, sourceFileName);
        return new DecodedFontResult(decoded, info);
    }

    private static String detectSignature(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) {
            return null;
        }
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        int b2 = bytes[offset + 2] & 0xFF;
        int b3 = bytes[offset + 3] & 0xFF;

        if (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) {
            return "0x00010000";
        }
        if (b0 == 't' && b1 == 'r' && b2 == 'u' && b3 == 'e') {
            return "true";
        }
        if (b0 == 't' && b1 == 't' && b2 == 'c' && b3 == 'f') {
            return "ttcf";
        }
        if (b0 == 'O' && b1 == 'T' && b2 == 'T' && b3 == 'O') {
            return "OTTO";
        }
        return null;
    }

    private static int readUInt16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static long readUInt32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
            | ((long) (bytes[offset + 1] & 0xFF) << 16)
            | ((long) (bytes[offset + 2] & 0xFF) << 8)
            | (long) (bytes[offset + 3] & 0xFF);
    }
}
