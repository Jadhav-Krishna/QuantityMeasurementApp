package com.quantityMeasurementApp;

import org.junit.jupiter.api.Test;
import com.quantityMeasurementApp.QuantityMeasurementApp.Feet;

public class QuantityMeasurementAppTest {

    @Test
    void testFeetEquality_SameValue() {
        Feet f1 = new Feet(1.0);
        Feet f2 = new Feet(1.0);
        assertTrue(f1.equals(f2));
    }

    @Test
    void testFeetEquality_DifferentValue() {
        Feet f1 = new Feet(1.0);
        Feet f2 = new Feet(2.0);
        assertFalse(f1.equals(f2));
    }

    @Test
    void testFeetEquality_NullComparison() {
        Feet f1 = new Feet(1.0);
        assertFalse(f1.equals(null));
    }

    @Test
    void testFeetEquality_DifferentClass() {
        Feet f1 = new Feet(1.0);
        String str = "1.0";
        assertFalse(f1.equals(str));
    }

    @Test
    void testFeetEquality_SameReference() {
        Feet f1 = new Feet(1.0);
        assertTrue(f1.equals(f1));
    }
}
