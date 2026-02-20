package com.UC2_InchEquality;

public class QuantityMeasurementApp {

	public static boolean checkInchesEquality(double v1, double v2) {
		return new Inches(v1).equals(new Inches(v2));
	}

	public static void main(String[] args) {
		System.out.println("Inches Equal: " + checkInchesEquality(1.0, 1.0));
	}
}
