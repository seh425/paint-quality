package com.paintshop.quality.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecognitionParser {
    private static final Pattern VIN_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])");
    private static final Pattern VIN_BODY = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
    private static final Pattern CONSERVATIVE_MODEL_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])([A-Z][0-9]{1,2})(?![A-Z0-9])");

    private RecognitionParser() {
    }

    /**
     * 先按标准VIN规则匹配；失败后把OCR常见的 O/0、I/1、Q/0 混淆还原，再按行滑动查找。
     */
    public static String extractVin(String... sources) {
        if (sources == null) {
            return "";
        }
        for (String source : sources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            Matcher matcher = VIN_PATTERN.matcher(source.toUpperCase(Locale.ROOT));
            if (matcher.find()) {
                return matcher.group();
            }
        }
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            for (String line : source.split("\\r?\\n")) {
                for (String token : line.split("[^A-Za-z0-9]+")) {
                    String normalized = normalizeVinText(token);
                    if (isLikelyVin(normalized)) {
                        return normalized;
                    }
                }
                String compact = normalizeVinText(line).replaceAll("[^A-Z0-9]", "");
                if (isLikelyVin(compact)) {
                    return compact;
                }
            }
        }
        return "";
    }

    private static boolean isLikelyVin(String candidate) {
        return candidate.length() == 17
                && VIN_BODY.matcher(candidate).matches()
                && countDigits(candidate) >= 3
                && countLetters(candidate) >= 4;
    }

    private static String normalizeVinText(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replace('O', '0')
                .replace('I', '1')
                .replace('Q', '0');
    }

    private static int countDigits(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static int countLetters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'A' && ch <= 'Z' && ch != 'I' && ch != 'O' && ch != 'Q') {
                count++;
            }
        }
        return count;
    }

    public static boolean isValidVin(String value) {
        return value != null && VIN_PATTERN.matcher(value.toUpperCase(Locale.ROOT)).matches();
    }

    /**
     * 找出条码正文：前6位为数字且总长不少于12位；允许OCR把数字识别成相近字母。
     */
    public static String barcodeCandidate(String... sources) {
        if (sources == null) {
            return "";
        }
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            String whole = clean(source);
            if (isBarcodeLike(whole)) {
                return whole;
            }
            String correctedWhole = correctBarcodePrefix(whole);
            if (correctedWhole != null) {
                return correctedWhole;
            }
            for (String token : source.split("[^A-Za-z0-9]+")) {
                String cleaned = token.toUpperCase(Locale.ROOT);
                if (isBarcodeLike(cleaned)) {
                    return cleaned;
                }
                String corrected = correctBarcodePrefix(cleaned);
                if (corrected != null) {
                    return corrected;
                }
            }
        }
        return "";
    }

    private static boolean isBarcodeLike(String value) {
        return value.length() >= 12 && value.length() <= 40
                && value.matches("\\d{6}[A-Z0-9]{6,}");
    }

    private static String correctBarcodePrefix(String value) {
        if (value.length() < 12 || VIN_BODY.matcher(value).matches()) {
            return null;
        }
        String rawPrefix = value.substring(0, 6);
        // 真实条码前6位是车号数字；至少已有3位数字才做OCR纠正，避免把VIN误判成条码
        if (countDigits(rawPrefix) < 3) {
            return null;
        }
        String prefix = fixDigits(rawPrefix);
        if (!prefix.matches("\\d{6}")) {
            return null;
        }
        String rest = value.substring(6);
        if (!rest.matches("[A-Z0-9]{6,}")) {
            return null;
        }
        return prefix + rest;
    }

    private static String fixDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case 'O':
                case 'Q':
                case 'D':
                    builder.append('0');
                    break;
                case 'I':
                case 'L':
                    builder.append('1');
                    break;
                case 'Z':
                    builder.append('2');
                    break;
                case 'S':
                    builder.append('5');
                    break;
                case 'G':
                    builder.append('6');
                    break;
                case 'T':
                    builder.append('7');
                    break;
                case 'B':
                    builder.append('8');
                    break;
                case 'A':
                    builder.append('4');
                    break;
                case 'P':
                    builder.append('9');
                    break;
                default:
                    builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * 车号取条码内容的前6位。
     */
    public static String vehicleNumberFromBarcode(String barcode) {
        String cleaned = clean(barcode);
        return cleaned.length() >= 6 ? cleaned.substring(0, 6) : "";
    }

    /**
     * 车型代码取条码内容的第11、12位。
     */
    public static String modelCodeFromBarcode(String barcode) {
        String cleaned = clean(barcode);
        return cleaned.length() >= 12 ? cleaned.substring(10, 12) : "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String matchingModelCode(String text, List<String> serverCodes) {
        if (text == null || serverCodes == null) {
            return "";
        }
        String upperText = text.toUpperCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        for (String code : serverCodes) {
            if (code != null && !code.trim().isEmpty()) {
                candidates.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        Collections.sort(candidates, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(right.length(), left.length());
            }
        });
        for (String code : candidates) {
            Pattern pattern = Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(code)
                    + "(?![A-Z0-9])");
            if (pattern.matcher(upperText).find()) {
                return code;
            }
        }
        return "";
    }

    public static String conservativeModelCode(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = CONSERVATIVE_MODEL_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : "";
    }
}
