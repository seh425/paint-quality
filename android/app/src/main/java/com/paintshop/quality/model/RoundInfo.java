package com.paintshop.quality.model;

import java.util.ArrayList;
import java.util.List;

public class RoundInfo {
    public int number;
    public String sequence = "";
    public String timeArea = "";
    public List<Integer> lines = new ArrayList<>();

    public List<Integer> availableLines() {
        if (lines == null || lines.isEmpty()) {
            List<Integer> fallback = new ArrayList<>();
            fallback.add(1);
            return fallback;
        }
        return lines;
    }

    public String label() {
        String time = timeArea == null ? "" : timeArea.replace("/", " ").trim();
        return (sequence == null ? "" : sequence) + "  " + time;
    }
}
