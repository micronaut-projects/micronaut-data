/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Arithmetic behind the {@code $inc} and {@code $mul} update operators.
 *
 * <p>The stored value decides the result type: an integral field stays integral, a
 * {@link BigDecimal} or {@link BigInteger} field keeps its exact representation, and only a
 * floating point field is computed in {@code double}. A result that does not fit the stored type
 * is reported as a {@link DataAccessException} rather than silently wrapping or widening.
 *
 * @since 5.2.0
 */
@Internal
public final class NumericUpdateOperations {

    private NumericUpdateOperations() {
    }

    /**
     * Applies an arithmetic update operand to the stored value.
     *
     * @param currentValue the value currently stored in the document
     * @param operand      the update operand
     * @param multiply     true for {@code $mul}, false for {@code $inc}
     * @param field        the document field, used for error reporting
     * @return the new value, or the current value when either side is not numeric
     */
    public static @Nullable Object apply(@Nullable Object currentValue,
                                         @Nullable Object operand,
                                         boolean multiply,
                                         String field) {
        if (!(currentValue instanceof Number currentNumber) || !(operand instanceof Number operandNumber)) {
            return currentValue;
        }
        try {
            return applyNumeric(currentValue, currentNumber, operandNumber, multiply);
        } catch (ArithmeticException e) {
            throw new DataAccessException("Arithmetic update of field [" + field
                + "] does not fit the stored type [" + currentValue.getClass().getSimpleName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Negates an operand, used by a generated subtraction.
     *
     * @param number the operand
     * @return the negated operand
     */
    public static Number negate(Number number) {
        return switch (number) {
            case BigDecimal decimal -> decimal.negate();
            case BigInteger integer -> integer.negate();
            case Double value -> -value;
            case Float value -> -value;
            default -> Math.negateExact(number.longValue());
        };
    }

    /**
     * Inverts an operand, used by a generated division. An integral operand is inverted exactly so
     * that the division is not forced through binary floating point.
     *
     * @param number the operand
     * @return the multiplicative inverse of the operand
     */
    public static Number reciprocal(Number number) {
        if (number instanceof Float || number instanceof Double) {
            return 1d / number.doubleValue();
        }
        return BigDecimal.ONE.divide(toBigDecimal(number), MathContext.DECIMAL128);
    }

    private static Object applyNumeric(Object currentValue, Number currentNumber, Number operandNumber, boolean multiply) {
        if (currentNumber instanceof BigDecimal || operandNumber instanceof BigDecimal) {
            BigDecimal current = toBigDecimal(currentNumber);
            BigDecimal delta = toBigDecimal(operandNumber);
            BigDecimal result = multiply ? current.multiply(delta) : current.add(delta);
            return narrowDecimal(result, currentValue);
        }
        if (currentNumber instanceof BigInteger || operandNumber instanceof BigInteger) {
            BigInteger current = toBigInteger(currentNumber);
            BigInteger delta = toBigInteger(operandNumber);
            BigInteger result = multiply ? current.multiply(delta) : current.add(delta);
            return currentValue instanceof BigInteger ? result : narrowDecimal(new BigDecimal(result), currentValue);
        }
        if (!(currentNumber instanceof Float || currentNumber instanceof Double
            || operandNumber instanceof Float || operandNumber instanceof Double)) {
            long current = currentNumber.longValue();
            long delta = operandNumber.longValue();
            long result = multiply ? Math.multiplyExact(current, delta) : Math.addExact(current, delta);
            return convertIntegralNumber(result, currentValue);
        }
        double current = currentNumber.doubleValue();
        double delta = operandNumber.doubleValue();
        return convertNumber(multiply ? current * delta : current + delta, currentValue);
    }

    /**
     * A decimal result of an operation on an integral field is brought back to that field's type,
     * truncating toward zero the way integral division does.
     */
    private static Object narrowDecimal(BigDecimal result, Object currentValue) {
        return switch (currentValue) {
            case Integer ignored -> Math.toIntExact(result.setScale(0, RoundingMode.DOWN).longValueExact());
            case Long ignored -> result.setScale(0, RoundingMode.DOWN).longValueExact();
            case Short ignored -> (short) result.setScale(0, RoundingMode.DOWN).longValueExact();
            case Byte ignored -> (byte) result.setScale(0, RoundingMode.DOWN).longValueExact();
            case Float ignored -> result.floatValue();
            case Double ignored -> result.doubleValue();
            default -> result;
        };
    }

    private static BigDecimal toBigDecimal(Number number) {
        return switch (number) {
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Byte ignored -> BigDecimal.valueOf(number.longValue());
            case Short ignored -> BigDecimal.valueOf(number.longValue());
            case Integer ignored -> BigDecimal.valueOf(number.longValue());
            case Long ignored -> BigDecimal.valueOf(number.longValue());
            default -> BigDecimal.valueOf(number.doubleValue());
        };
    }

    private static BigInteger toBigInteger(Number number) {
        return number instanceof BigInteger integer ? integer : BigInteger.valueOf(number.longValue());
    }

    private static Object convertIntegralNumber(long value, Object currentValue) {
        return switch (currentValue) {
            case Integer ignored -> Math.toIntExact(value);
            case Short ignored -> narrowExact(value, Short.MIN_VALUE, Short.MAX_VALUE, "short");
            case Byte ignored -> narrowExact(value, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
            default -> value;
        };
    }

    private static Object narrowExact(long value, long min, long max, String type) {
        if (value < min || value > max) {
            throw new ArithmeticException(type + " overflow");
        }
        return "short".equals(type) ? (short) value : (byte) value;
    }

    private static Object convertNumber(double value, Object currentValue) {
        return switch (currentValue) {
            case Integer ignored -> (int) value;
            case Long ignored -> (long) value;
            case Float ignored -> (float) value;
            case Short ignored -> (short) value;
            case Byte ignored -> (byte) value;
            default -> value;
        };
    }
}
