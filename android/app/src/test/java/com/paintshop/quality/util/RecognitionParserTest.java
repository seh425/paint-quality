package com.paintshop.quality.util;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecognitionParserTest {
    private static final String VALID_VIN = "1HGCM82633A004352";

    @Test
    public void extractsBarcodeVinAndVehicleNumberIsLastSixCharacters() {
        String vin = RecognitionParser.extractVin("barcode:" + VALID_VIN);

        assertEquals(VALID_VIN, vin);
        assertEquals("004352", RecognitionParser.vehicleNumberFromVin(vin));
    }

    @Test
    public void rejectsInvalidVinWithoutInventingVehicleNumber() {
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435I"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435O"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435Q"));
        assertFalse(RecognitionParser.isValidVin("1HGCM82633A00435"));
        assertEquals("", RecognitionParser.extractVin("VIN=1HGCM82633A00435I"));
        assertEquals("", RecognitionParser.vehicleNumberFromVin("invalid"));
    }

    @Test
    public void extractsProvidedLabelSample() {
        String vin = RecognitionParser.extractVin("LSGZS83LXTD144176");

        assertEquals("144176", RecognitionParser.vehicleNumberFromVin(vin));
        assertEquals("B8", RecognitionParser.matchingModelCode(
                "CY05 C3U 23\nB8\nLSGZS83LXTD144176", Arrays.asList("B8")));
    }

    @Test
    public void matchesB8FromServerModelCodeList() {
        String code = RecognitionParser.matchingModelCode(
                "车型代码：B8\n其他文字", Arrays.asList("X1", "B8"));

        assertEquals("B8", code);
    }

    @Test
    public void conservativeCodeRequiresSeparateToken() {
        assertEquals("C7", RecognitionParser.conservativeModelCode("临时代码 C7"));
        assertEquals("", RecognitionParser.conservativeModelCode("ABC78"));
        assertTrue(RecognitionParser.isValidVin(VALID_VIN.toLowerCase()));
    }
}
