package com.paintshop.quality.model;

public class RoundInfo {
    public int number;
    public String sequence = "";
    public String timeArea = "";

    public String label() {
        String time = timeArea == null ? "" : timeArea.replace("/", " ").trim();
        return (sequence == null ? "" : sequence) + "  " + time;
    }
}
