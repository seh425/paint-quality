package com.paintshop.quality.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the 5 visible rows of an HMI screenshot.
 * Each row is expected to contain a 6-digit car number followed by a model value
 * such as "98", "89", "E2UN" or "E2YB".
 */
public final class HmiParser {

    private static final Pattern CAR_NUMBER = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final Pattern MODEL_ALPHA = Pattern.compile("[A-Z][A-Z0-9]{1,5}");
    private static final Pattern MODEL_DIGITS = Pattern.compile("\\d{2}");

    private HmiParser() {
    }

    public static List<Row> parse(String text) {
        List<Row> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        Set<String> seen = new HashSet<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.toUpperCase(Locale.ROOT)
                    .replace('，', ' ').replace(',', ' ')
                    .replace('｜', ' ').replace('|', ' ');
            Matcher matcher = CAR_NUMBER.matcher(line);
            while (matcher.find() && rows.size() < 5) {
                String carNumber = matcher.group(1);
                if (!seen.add(carNumber)) {
                    continue;
                }
                String model = findModel(line.substring(matcher.end()));
                rows.add(new Row(carNumber, model));
            }
        }
        return rows;
    }

    private static String findModel(String rest) {
        String[] tokens = rest.trim().split("[\\s\\t]+");
        for (int i = 0; i < Math.min(tokens.length, 6); i++) {
            String token = tokens[i].replaceAll("[^A-Z0-9]", "");
            if (token.isEmpty()) {
                continue;
            }
            if (MODEL_DIGITS.matcher(token).matches()) {
                return token;
            }
            if (MODEL_ALPHA.matcher(token).matches()
                    && !"AS".equals(token) && !"PLC".equals(token)
                    && !"NA".equals(token) && !"NOK".equals(token)) {
                return token;
            }
        }
        return "";
    }

    public static final class Row {
        public final String carNumber;
        public final String modelValue;

        public Row(String carNumber, String modelValue) {
            this.carNumber = carNumber;
            this.modelValue = modelValue;
        }
    }
}
