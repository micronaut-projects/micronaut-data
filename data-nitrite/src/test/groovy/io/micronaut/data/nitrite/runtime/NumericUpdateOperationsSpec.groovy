package io.micronaut.data.nitrite.runtime

import io.micronaut.data.exceptions.DataAccessException
import spock.lang.Specification

/**
 * Arithmetic applied by {@code $inc} and {@code $mul} updates: the stored field keeps its own
 * numeric type, exact types are not collapsed to double, and a result that does not fit the
 * stored type is reported as a data access failure.
 */
class NumericUpdateOperationsSpec extends Specification {

    void "an integral increment keeps the stored integral type"() {
        expect:
        NumericUpdateOperations.apply(10 as Integer, 5 as Integer, false, "priority") == 15
        NumericUpdateOperations.apply(10 as Integer, 5 as Integer, false, "priority") instanceof Integer
        NumericUpdateOperations.apply(10L, 5 as Integer, false, "priority") instanceof Long
        NumericUpdateOperations.apply((short) 10, 5 as Integer, false, "priority") instanceof Short
    }

    void "an integral increment that overflows the stored type is a data access exception"() {
        when:
        NumericUpdateOperations.apply(Integer.MAX_VALUE, 1 as Integer, false, "priority")

        then:
        def e = thrown(DataAccessException)
        e.message.contains("priority")
    }

    void "an integral multiplication that overflows a long is a data access exception"() {
        when:
        NumericUpdateOperations.apply(Long.MAX_VALUE, 2L, true, "total")

        then:
        thrown(DataAccessException)
    }

    void "decimal arithmetic keeps exact precision"() {
        expect:
        NumericUpdateOperations.apply(new BigDecimal("0.1"), new BigDecimal("0.2"), false, "amount") == new BigDecimal("0.3")
        NumericUpdateOperations.apply(new BigInteger("100"), new BigInteger("3"), true, "count") == new BigInteger("300")
    }

    void "floating point arithmetic keeps the stored floating point type"() {
        expect:
        NumericUpdateOperations.apply(1.5d, 2.0d, true, "ratio") == 3.0d
        NumericUpdateOperations.apply(1.5d, 2.0d, true, "ratio") instanceof Double
    }

    void "a division of an integral field stays integral"() {
        expect:
        def half = NumericUpdateOperations.reciprocal(2 as Integer)
        NumericUpdateOperations.apply(10 as Integer, half, true, "priority") == 5
        NumericUpdateOperations.apply(10 as Integer, half, true, "priority") instanceof Integer
        // Truncating toward zero matches integral division on the entity type.
        NumericUpdateOperations.apply(5 as Integer, half, true, "priority") == 2
    }

    void "a negated operand keeps the stored type"() {
        expect:
        def minusThree = NumericUpdateOperations.negate(3 as Integer)
        NumericUpdateOperations.apply(10 as Integer, minusThree, false, "priority") == 7
        NumericUpdateOperations.apply(10 as Integer, minusThree, false, "priority") instanceof Integer
    }

    void "a non numeric current value is left untouched"() {
        expect:
        NumericUpdateOperations.apply("text", 1 as Integer, false, "type") == "text"
        NumericUpdateOperations.apply(null, 1 as Integer, false, "type") == null
    }

    void "a floating point operand narrows the result to the stored integral or floating type"() {
        expect:
        def result = NumericUpdateOperations.apply(current, 2.5d, false, "x")
        expectedType.isInstance(result)

        where:
        current       | expectedType
        10 as Integer | Integer
        10L           | Long
        10.5f         | Float
        (short) 10    | Short
        (byte) 10     | Byte
    }

    void "a BigDecimal operand narrows the result to the stored numeric type"() {
        expect:
        def result = NumericUpdateOperations.apply(current, new BigDecimal("5"), false, "x")
        expectedType.isInstance(result)

        where:
        current       | expectedType
        10 as Integer | Integer
        10L           | Long
        (short) 10    | Short
        (byte) 10     | Byte
        10.5f         | Float
        10.5d         | Double
    }

    void "a BigDecimal current value converts a non decimal operand of any integral width"() {
        expect:
        NumericUpdateOperations.apply(new BigDecimal("1"), operand, false, "amount") == expected

        where:
        operand       | expected
        (byte) 1      | new BigDecimal("2")
        (short) 1     | new BigDecimal("2")
        1 as Integer  | new BigDecimal("2")
        1L            | new BigDecimal("2")
        2.5d          | new BigDecimal("3.5")
    }

    void "negate preserves the operand's numeric type"() {
        expect:
        def result = NumericUpdateOperations.negate(number)
        result == expected
        result.class == expected.class

        where:
        number               | expected
        3 as Integer         | -3L
        3L                   | -3L
        new BigDecimal("3")  | new BigDecimal("-3")
        new BigInteger("3")  | new BigInteger("-3")
        3.5d                 | -3.5d
        3.5f                 | -3.5f
    }

    void "reciprocal of a floating point operand divides in double precision"() {
        expect:
        NumericUpdateOperations.reciprocal(number) == 0.5d

        where:
        number << [2.0d, 2.0f]
    }

    void "reciprocal of an exact operand stays exact"() {
        expect:
        NumericUpdateOperations.reciprocal(number) == new BigDecimal("0.5")

        where:
        number << [2 as Integer, new BigDecimal("2")]
    }

    void "a multiplication of a narrow integral field keeps the stored type"() {
        expect:
        def result = NumericUpdateOperations.apply(current, 2 as Integer, true, "priority")
        result == 20
        current.class.isInstance(result)

        where:
        current << [(short) 10, (byte) 10]
    }

    void "a byte increment within range keeps the stored byte type"() {
        expect:
        NumericUpdateOperations.apply(10 as Byte, 5 as Integer, false, "priority") == 15
        NumericUpdateOperations.apply(10 as Byte, 5 as Integer, false, "priority") instanceof Byte
    }

    void "an integral increment that overflows a short or byte field is a data access exception"() {
        when:
        NumericUpdateOperations.apply(current, 1 as Integer, false, "priority")

        then:
        thrown(DataAccessException)

        where:
        current << [Short.MAX_VALUE, Byte.MAX_VALUE]
    }
}
