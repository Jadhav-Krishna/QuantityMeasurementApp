package com.app.measurementservice.service;

import com.app.measurementservice.domain.MeasurementCategory;
import com.app.measurementservice.domain.MeasurementOperation;
import org.springframework.stereotype.Component;

@Component
public class MeasurementEngine {

    private static final double KM_TO_MILES = 0.621371;
    private static final double KG_TO_LBS = 2.20462;
    private static final double LITRE_TO_GALLONS = 0.264172;
    private static final double EPSILON = 1e-9;

    // Canonical unit for each category: LENGTH=KM, WEIGHT=KG, VOLUME=L, TEMPERATURE=C
    private static final double FEET_TO_KM = 0.0003048;
    private static final double INCHES_TO_KM = 0.0000254;
    private static final double YARDS_TO_KM = 0.0009144;
    private static final double CM_TO_KM = 0.00001;
    private static final double GRAM_TO_KG = 0.001;
    private static final double POUND_TO_KG = 0.453592;
    private static final double ML_TO_L = 0.001;
    private static final double GALLON_TO_L = 3.78541;

    public double convert(MeasurementCategory category, double value, String fromUnit, String toUnit) {
        return round(convertFromCanonical(category, toCanonical(category, value, fromUnit), toUnit));
    }

    public ComputationResult compute(
            MeasurementCategory category,
            MeasurementOperation operation,
            double leftValue,
            String leftUnit,
            double rightValue,
            String rightUnit,
            String resultUnit
    ) {
        if (operation == MeasurementOperation.CONVERT) {
            throw new IllegalArgumentException("Use the convert endpoint for conversion requests");
        }
        if (category == MeasurementCategory.TEMPERATURE && operation != MeasurementOperation.COMPARE) {
            throw new IllegalArgumentException("Temperature supports compare only in this service");
        }

        double leftCanonical = toCanonical(category, leftValue, leftUnit);
        double rightCanonical = toCanonical(category, rightValue, rightUnit);

        return switch (operation) {
            case ADD -> numericResult(category, leftCanonical + rightCanonical, resultUnit);
            case SUBTRACT -> numericResult(category, leftCanonical - rightCanonical, resultUnit);
            case DIVIDE -> new ComputationResult(
                    null,
                    round(divide(leftCanonical, rightCanonical)),
                    null,
                    "Division completed in canonical units"
            );
            case COMPARE -> new ComputationResult(
                    null,
                    null,
                    Math.abs(leftCanonical - rightCanonical) < EPSILON,
                    "Comparison completed"
            );
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private ComputationResult numericResult(MeasurementCategory category, double canonicalValue, String resultUnit) {
        String safeResultUnit = resolveResultUnit(category, resultUnit);
        return new ComputationResult(
                safeResultUnit,
                round(convertFromCanonical(category, canonicalValue, safeResultUnit)),
                null,
                "Computation completed"
        );
    }

    private String resolveResultUnit(MeasurementCategory category, String requestedUnit) {
        if (requestedUnit == null || requestedUnit.isBlank()) {
            return switch (category) {
                case LENGTH -> "FEET";
                case WEIGHT -> "KILOGRAM";
                case VOLUME -> "LITRE";
                case TEMPERATURE -> "CELSIUS";
            };
        }
        return requestedUnit.toUpperCase();
    }

    private double toCanonical(MeasurementCategory category, double value, String unit) {
        String u = unit.toUpperCase();
        return switch (category) {
            case LENGTH -> switch (u) {
                case "KM" -> value;
                case "MILES" -> value / KM_TO_MILES;
                case "FEET", "FOOT", "FT" -> value * FEET_TO_KM;
                case "INCHES", "INCH", "IN" -> value * INCHES_TO_KM;
                case "YARDS", "YARD", "YD" -> value * YARDS_TO_KM;
                case "CENTIMETERS", "CENTIMETER", "CM" -> value * CM_TO_KM;
                default -> throw unsupported(category, unit);
            };
            case WEIGHT -> switch (u) {
                case "KG", "KILOGRAM", "KILOGRAMS" -> value;
                case "LBS", "POUND", "POUNDS" -> value * POUND_TO_KG;
                case "GRAM", "GRAMS", "G" -> value * GRAM_TO_KG;
                default -> throw unsupported(category, unit);
            };
            case VOLUME -> switch (u) {
                case "L", "LITRE", "LITER", "LITRES", "LITERS" -> value;
                case "GALLONS", "GALLON" -> value * GALLON_TO_L;
                case "ML", "MILLILITRE", "MILLILITER", "MILLILITRES", "MILLILITERS" -> value * ML_TO_L;
                default -> throw unsupported(category, unit);
            };
            case TEMPERATURE -> switch (u) {
                case "C", "CELSIUS" -> value;
                case "F", "FAHRENHEIT" -> (value - 32) * 5.0 / 9.0;
                case "K", "KELVIN" -> value - 273.15;
                default -> throw unsupported(category, unit);
            };
        };
    }

    private double convertFromCanonical(MeasurementCategory category, double value, String unit) {
        String u = unit.toUpperCase();
        return switch (category) {
            case LENGTH -> switch (u) {
                case "KM" -> value;
                case "MILES" -> value * KM_TO_MILES;
                case "FEET", "FOOT", "FT" -> value / FEET_TO_KM;
                case "INCHES", "INCH", "IN" -> value / INCHES_TO_KM;
                case "YARDS", "YARD", "YD" -> value / YARDS_TO_KM;
                case "CENTIMETERS", "CENTIMETER", "CM" -> value / CM_TO_KM;
                default -> throw unsupported(category, unit);
            };
            case WEIGHT -> switch (u) {
                case "KG", "KILOGRAM", "KILOGRAMS" -> value;
                case "LBS", "POUND", "POUNDS" -> value / POUND_TO_KG;
                case "GRAM", "GRAMS", "G" -> value / GRAM_TO_KG;
                default -> throw unsupported(category, unit);
            };
            case VOLUME -> switch (u) {
                case "L", "LITRE", "LITER", "LITRES", "LITERS" -> value;
                case "GALLONS", "GALLON" -> value / GALLON_TO_L;
                case "ML", "MILLILITRE", "MILLILITER", "MILLILITRES", "MILLILITERS" -> value / ML_TO_L;
                default -> throw unsupported(category, unit);
            };
            case TEMPERATURE -> switch (u) {
                case "C", "CELSIUS" -> value;
                case "F", "FAHRENHEIT" -> (value * 9.0 / 5.0) + 32;
                case "K", "KELVIN" -> value + 273.15;
                default -> throw unsupported(category, unit);
            };
        };
    }

    private IllegalArgumentException unsupported(MeasurementCategory category, String unit) {
        return new IllegalArgumentException("Unsupported unit '" + unit + "' for category " + category);
    }

    private double divide(double left, double right) {
        if (Math.abs(right) < EPSILON) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return left / right;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ComputationResult(
            String resultUnit,
            Double resultValue,
            Boolean comparisonResult,
            String message
    ) {
    }
}
