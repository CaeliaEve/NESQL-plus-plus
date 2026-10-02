package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Reduced probabilities. Declared rates and native random thresholds have distinct meanings. */
public final class Chance {
    private Chance() {}

    public static JsonObject of(long numerator, long denominator) {
        if (denominator <= 0 || numerator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("Probability must be within 0..1");
        }
        long left = numerator, right = denominator;
        while (right != 0) { long next = left % right; left = right; right = next; }
        return object("numerator", Long.toString(numerator / left), "denominator", Long.toString(denominator / left));
    }

    /** maximum is 1 for product rates and 100 for Forestry mutation percentages. */
    public static JsonObject decimal(float value, int maximum) {
        if (!Float.isFinite(value) || maximum <= 0 || value < 0 || value > maximum) {
            throw new IllegalArgumentException("Invalid decimal probability");
        }
        BigDecimal decimal = new BigDecimal(Float.toString(value)).stripTrailingZeros();
        BigInteger numerator = decimal.unscaledValue();
        BigInteger denominator = BigInteger.valueOf(maximum);
        if (decimal.scale() > 0) denominator = denominator.multiply(BigInteger.TEN.pow(decimal.scale()));
        else numerator = numerator.multiply(BigInteger.TEN.pow(-decimal.scale()));
        BigInteger divisor = numerator.gcd(denominator);
        return of(numerator.divide(divisor).longValueExact(), denominator.divide(divisor).longValueExact());
    }

    /** Exact fraction of Random.nextFloat's 2^24 possible values satisfying < or <=. */
    public static JsonObject nextFloat(float threshold, boolean inclusive) {
        if (Float.isNaN(threshold) || threshold < 0) return of(0, 1);
        if (threshold >= 1) return of(1, 1);
        long samples = 1L << 24;
        double scaled = (double) threshold * samples; // Float -> double and power-of-two scaling are exact.
        long accepted = inclusive ? (long) Math.floor(scaled) + 1 : (long) Math.ceil(scaled);
        return of(accepted, samples);
    }
}
