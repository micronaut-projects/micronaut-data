package io.micronaut.data.nitrite.runtime.query

import spock.lang.Specification
import spock.lang.Unroll

import java.math.BigDecimal

/**
 * Property-based coverage for the double/BigDecimal round-trip guard in the
 * decimal-literal branch of {@code NitriteQueryParser}'s number parsing.
 *
 * Invariant under test:
 *   For any decimal literal s, let d = new BigDecimal(s).
 *   The parser returns a `double` IFF BigDecimal.valueOf(Double.parseDouble(s)) == d
 *   (compareTo, so scale differences don't count as inequality).
 *   In every case, re-wrapping the result in a BigDecimal must reconstruct the exact
 *   same numeric value as `d` -- i.e. no branch is allowed to silently lose precision.
 *
 * The parsing logic itself lives on a private inner class with no public seam, so this
 * drives it through the same public entry point production code uses:
 * {@link NitriteQueryParser#parseJson(String)}, wrapping each literal as {@code {"v":<literal>}}
 * and reading back the "v" entry.
 */
class NumberLiteralParsingPropertySpec extends Specification {

    // Fixed seed => reproducible failures. Bump the seed (or log it) if you want to explore
    // a fresh input space; on CI failure, print the seed so the exact run can be replayed.
    private static final long SEED = 20260814L
    private static final int ITERATIONS = 5000

    def parser = new NitriteQueryParser()

    private Object parseNumber(String literal) {
        (parser.parseJson('{"v":' + literal + '}') as Map).v
    }

    def "decimal round-trip guard holds for a large batch of random decimal literals"() {
        given:
        Random rng = new Random(SEED)
        List<String> failures = []

        when:
        ITERATIONS.times { i ->
            String literal = randomDecimalLiteral(rng)
            BigDecimal exact = new BigDecimal(literal)

            Object result = parseNumber(literal)
            BigDecimal reconstructed = (result instanceof Double)
                ? BigDecimal.valueOf((Double) result)
                : (BigDecimal) result

            boolean typeMatchesGuard = (result instanceof Double) ==
                doubleRoundTripsExactly(literal, exact)
            boolean valuePreserved = reconstructed.compareTo(exact) == 0

            if (!typeMatchesGuard || !valuePreserved) {
                failures << "literal=$literal exact=$exact result=$result " +
                    "(${result?.class?.simpleName}) typeMatchesGuard=$typeMatchesGuard " +
                    "valuePreserved=$valuePreserved"
            }
        }

        then:
        // Dump every failing case at once rather than stopping at the first -- much faster
        // to spot a pattern (e.g. "always fails near exponent boundary X"). Wrapped in `if`
        // rather than a bare statement: Spock auto-asserts every top-level then: expression
        // via Groovy truth, and an empty list is falsy, so a bare `failures.each {}` would
        // itself fail the passing case.
        if (failures) {
            failures.each { println it }
        }
        failures.isEmpty()
    }

    @Unroll
    def "known tricky decimal literal '#literal' preserves value and picks the right branch"() {
        given:
        BigDecimal exact = new BigDecimal(literal)

        when:
        Object result = parseNumber(literal)

        then:
        (result instanceof Double) == expectDouble
        BigDecimal reconstructed = (result instanceof Double)
            ? BigDecimal.valueOf((Double) result)
            : (BigDecimal) result
        reconstructed.compareTo(exact) == 0

        where:
        literal                                  | expectDouble
        "0.1"                                    | true
        "1.5"                                    | true
        "100.0"                                  | true
        "0.1000000000000000000000001"            | false   // decimal digits beyond double precision
        "1.0000000000000000001"                  | false
        "9007199254740993.0"                     | false   // 2^53 + 1, first integer double can't represent exactly
        "9007199254740992.0"                     | true    // 2^53, exactly representable
        "4.9E-324"                                | true    // smallest positive double (subnormal), exact
        "1.7976931348623157E308"                  | true    // Double.MAX_VALUE, exact
        "1.7976931348623159E308"                  | false   // just past MAX_VALUE -> Infinity -> not finite -> BigDecimal
    }

    // ---- generators -------------------------------------------------------

    /**
     * Builds a random decimal literal string covering:
     *  - varying integer-part length (0 to 20 digits)
     *  - varying fractional-part length (0 to 25 digits) to stress precision loss
     *  - occasional scientific notation with a wide exponent range
     *  - random sign
     */
    private static String randomDecimalLiteral(Random rng) {
        StringBuilder sb = new StringBuilder()
        if (rng.nextBoolean()) sb.append('-')

        int intDigits = rng.nextInt(20) + 1
        sb.append(randomDigits(rng, intDigits, true))

        sb.append('.')
        int fracDigits = rng.nextInt(25) + 1
        sb.append(randomDigits(rng, fracDigits, false))

        // 1 in 3 chance of adding an exponent, including some extreme ones to force
        // the Double.isFinite() branch of the guard.
        if (rng.nextInt(3) == 0) {
            sb.append(rng.nextBoolean() ? 'e' : 'E')
            // Exactly one of +, -, or no sign — never both, or BigDecimal rejects the exponent.
            int sign = rng.nextInt(3)
            if (sign == 1) sb.append('+')
            else if (sign == 2) sb.append('-')
            int exponentMagnitude = rng.nextBoolean() ? rng.nextInt(20) : rng.nextInt(400)
            sb.append(exponentMagnitude)
        }
        return sb.toString()
    }

    private static String randomDigits(Random rng, int count, boolean allowLeadingZero) {
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < count; i++) {
            int digit = rng.nextInt(10)
            if (i == 0 && !allowLeadingZero && digit == 0) {
                digit = rng.nextInt(9) + 1
            }
            sb.append(Character.forDigit(digit, 10))
        }
        return sb.toString()
    }

    private static boolean doubleRoundTripsExactly(String literal, BigDecimal exact) {
        double d
        try {
            d = Double.parseDouble(literal)
        } catch (NumberFormatException ignored) {
            return false
        }
        return Double.isFinite(d) && exact.compareTo(BigDecimal.valueOf(d)) == 0
    }
}
