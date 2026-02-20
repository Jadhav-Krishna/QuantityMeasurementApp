package com.UC1_FeetEquality;

public class QuantityMeasurementApp {

	public static class Feet {
		private final double value;

		public Feet(double value) {
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

			Feet other = (Feet) obj;

			return Double.compare(this.value, other.value) == 0;
		}
	}

	public static void main(String[] args) {

		Feet f1 = new Feet(1.0);
		Feet f2 = new Feet(1.0);

		System.out.println("Equal: " + f1.equals(f2));
	}
}