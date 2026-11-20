package com.paintshop.quality.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecognitionParserTest {
    private static final String VALID_VIN = "1HGCM82633A004352";

    @Test
    public void readsVehicleNumberAndModelCodeFromBarcode() {
        String barcode = RecognitionParser.barcodeCandidate("563279B810 88011045");

        assertEquals("563279B81088011045", barcode);
        assertEquals("563279", RecognitionParser.vehicleNumberFromBarcode(barcode));
        assertEquals("88", RecognitionParser.modelCodeFromBarcode(barcode));
    }

    @Test
    public void ignoresBarcodeWithoutLeadingSixDigits() {
        assertEquals("", RecognitionParser.barcodeCandidate("LSGZS83LXTD144176"));
        assertEquals("", RecognitionParser.vehicleNumberFromBarcode("12345"));
        assertEquals("", RecognitionParser.modelCodeFromBarcode("12345678901"));
    }

    @Test
    public void extractsVinFromLabelText() {
        String vin = RecognitionParser.extractVin("CY05 C3U 23\nB8\nLSGZS83LXTD144176");

        assertEquals("LSGZS83LXTD144176", vin);
    }

    @Test
    public void rejectsInvalidVin() {
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435I"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435O"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435Q"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435"));
        assertEquals("", RecognitionParser.extractVin("VIN=1HGCM82633A00435I"));
    }

    @Test
    public void acceptsValidVinInLowerCase() {
        assertTrue(RecognitionParser.isValidVin(VALID_VIN.toLowerCase()));
    }
}
