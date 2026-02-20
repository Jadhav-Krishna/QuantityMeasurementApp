package com.UC2_InchEquality;

public class QuantityMeasurementApp {
	public static class Inches {

		private final double value;

		public Inches(double value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {

			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;

			Inches other = (Inches) obj;
			return Double.compare(this.value, other.value) == 0;
		}
	}

	public static boolean checkInchesEquality(double v1, double v2) {
		return new Inches(v1).equals(new Inches(v2));
	}

	public static void main(String[] args) {
		System.out.println("Inches Equal: " + checkInchesEquality(1.0, 1.0));
	}
}
