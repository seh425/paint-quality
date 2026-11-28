package com.paintshop.quality.model;

import java.util.ArrayList;
import java.util.List;

public class InspectionDraft {
    public String workDate = "";
    public int roundNumber;
    public int lineNumber = 1;
    public int currentIndex;
    public boolean doubleCheck;
    public List<DraftVehicle> vehicles = new ArrayList<>();

    public static InspectionDraft empty(String workDate, int roundNumber, int lineNumber) {
        InspectionDraft draft = new InspectionDraft();
        draft.workDate = workDate;
        draft.roundNumber = roundNumber;
        draft.lineNumber = lineNumber;
        for (int i = 1; i <= 5; i++) {
            draft.vehicles.add(new DraftVehicle(i));
        }
        return draft;
    }

    public boolean isValidFor(String date, int round, int line) {
        return date.equals(workDate) && roundNumber == round && lineNumber == line
                && vehicles != null && vehicles.size() == 5;
    }
}
