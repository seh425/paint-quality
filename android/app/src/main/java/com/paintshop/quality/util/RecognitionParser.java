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
    private static final Pattern CONSERVATIVE_MODEL_PATTERN = Pattern.compile(
            "(?<![A-Z0-9])([A-Z][0-9]{1,2})(?![A-Z0-9])");

    private RecognitionParser() {
    }

    public static String extractVin(String... sources) {
        if (sources == null) {
            return "";
        }
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            Matcher matcher = VIN_PATTERN.matcher(source.toUpperCase(Locale.ROOT));
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }

    public static boolean isValidVin(String value) {
        return value != null && VIN_PATTERN.matcher(value.toUpperCase(Locale.ROOT)).matches();
    }

    public static String vehicleNumberFromVin(String vin) {
        return isValidVin(vin) ? vin.substring(11).toUpperCase(Locale.ROOT) : "";
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
