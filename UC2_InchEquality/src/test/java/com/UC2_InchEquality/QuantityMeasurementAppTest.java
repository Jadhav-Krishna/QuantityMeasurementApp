package com.UC2_InchEquality;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QuantityMeasurementAppTest {

    @Test
    void testInchesEquality_SameValue() {
        assertTrue(new Inches(1.0).equals(new Inches(1.0)));
    }

    @Test
    void testInchesEquality_DifferentValue() {
        assertFalse(new Inches(1.0).equals(new Inches(2.0)));
    }

    @Test
    void testInchesEquality_NullComparison() {
        assertFalse(new Inches(1.0).equals(null));
    }

    @Test
    void testInchesEquality_DifferentClass() {
        assertFalse(new Inches(1.0).equals("1.0"));
    }

    @Test
    void testInchesEquality_SameReference() {
        Inches i = new Inches(1.0);
        assertTrue(i.equals(i));
    }
}