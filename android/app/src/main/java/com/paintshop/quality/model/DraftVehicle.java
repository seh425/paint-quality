package com.paintshop.quality.model;

public class DraftVehicle {
    public int vehiclePosition;
    public String vin = "";
    public String vehicleNumber = "";
    public String modelCode = "";
    public String vehicleModel = "";
    public String status = "NA";
    public String issueNote = "";

    public DraftVehicle() {
    }

    public DraftVehicle(int vehiclePosition) {
        this.vehiclePosition = vehiclePosition;
    }
}
