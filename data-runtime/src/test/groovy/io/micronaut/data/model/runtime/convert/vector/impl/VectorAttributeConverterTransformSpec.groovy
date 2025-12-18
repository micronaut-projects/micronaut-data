package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.DialectConversionContext
import io.micronaut.data.runtime.mapper.ResultReader
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

/**
 * Verifies transform/conversion behavior of AbstractVectorAttributeConverter:
 * - Delegation to a VectorTypeConvertor when a converter is registered for a Dialect
 * - Fallback to textual representation for Oracle when no converter is registered
 * - readFromResultSet delegates to ResultReader with persisted type
 * - convertToEntityValue delegates through VectorTypeConvertor
 * - Column definition rendering per dialect
 */
class VectorAttributeConverterTransformSpec extends Specification {

    /**
     * Minimal concrete converter for testing that extends the package-private
     * AbstractVectorAttributeConverter from the same package.
     */
    static class TestDoubleVectorConverter extends AbstractVectorAttributeConverter<DoubleVector, Object> {
        TestDoubleVectorConverter(Map<String, VectorTypeConvertor<?>> converterMap) {
            super(converterMap, DoubleVector.class)
        }

        @Override
        String getOracleType() {
            return "FLOAT64"
        }
    }

    def "convertToPersistedValue uses converter map for POSTGRES dialect"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConvertor) {
            getPersistedType() >> persistedType
            getDialect() >> Dialect.POSTGRES
            getName() >> "postgres-vector"
            // entity -> persisted
            convert(_, _) >> { Vector v, Class t ->
                "pg:${v.toDoubleArray().join(',')}"
            }
            // persisted -> entity (not used in this test)
            convert(_, Class) >> { Object obj, Class target ->
                Vector.of(9d, 9d)
            }
        }
        def converter = new TestDoubleVectorConverter(["POSTGRES": delegatingConverter])
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.POSTGRES
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        def persisted = converter.convertToPersistedValue(v, ctx)

        then:
        persisted == "pg:1.0,2.0"
    }

    def "convertToPersistedValue falls back to textual representation for ORACLE when no converter exists"() {
        given:
        def converter = new TestDoubleVectorConverter([:]) // no converter in the map
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.ORACLE
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        def persisted = converter.convertToPersistedValue(v, ctx)

        then:
        // Fallback is Arrays.toString(double[]) e.g. "[1.0, 2.0]"
        persisted == "[1.0, 2.0]"
    }

    def "readFromResultSet delegates to ResultReader with persisted type for POSTGRES"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConvertor) {
            getPersistedType() >> persistedType
            getDialect() >> Dialect.POSTGRES
            getName() >> "postgres-vector"
            convert(_, _) >> { Vector v, Class t -> "pg:${v.toDoubleArray().join(',')}" }
            convert(_, Class) >> { Object obj, Class target -> Vector.of(1d, 2d) }
        }
        def converter = new TestDoubleVectorConverter(["POSTGRES": delegatingConverter])
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.POSTGRES
        }
        def rr = Stub(ResultReader) {
            getRequiredValue(_, _, _) >> { rs, col, clazz ->
                // Return a value that includes the class name to assert the persisted type passed down
                "read:${clazz.name}"
            }
        }

        when:
        def val = converter.readFromResultSet(ctx, rr as ResultReader<Object, Object>, new Object(), "col")

        then:
        val == "read:java.lang.String"
    }

    def "convertToEntityValue delegates to converter for POSTGRES"() {
        given:
        def delegatingConverter = Stub(VectorTypeConvertor) {
            getPersistedType() >> String
            getDialect() >> Dialect.POSTGRES
            getName() >> "postgres-vector"
            convert(_ as Vector, _ as Class) >> { Vector v, Class t -> "pg:${v.toDoubleArray().join(',')}" }
            convert(_ as String, _ as Class) >> { String obj, Class target ->
                // persisted -> entity path
                Vector.of(7d, 8d)
            }
        }
        def converter = new TestDoubleVectorConverter(["POSTGRES": delegatingConverter])
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.POSTGRES
        }

        when:
        def entity = converter.convertToEntityValue("ignored-persisted", ctx)

        then:
        entity instanceof DoubleVector
        entity.toDoubleArray().toList() == [7d, 8d]
    }

    def "getColumnDefinition renders per dialect"() {
        given:
        def converter = new TestDoubleVectorConverter([:])

        expect:
        converter.getColumnDefinition(OptionalInt.of(3), Dialect.POSTGRES) == "vector(3)"
        converter.getColumnDefinition(OptionalInt.empty(), Dialect.POSTGRES) == "vector"
        converter.getColumnDefinition(OptionalInt.of(5), Dialect.ORACLE) == "VECTOR(5,FLOAT64)"
        converter.getColumnDefinition(OptionalInt.empty(), Dialect.ORACLE) == "VECTOR(*,FLOAT64)"
        converter.getColumnDefinition(OptionalInt.of(7), Dialect.MYSQL) == "VECTOR(7)"
        converter.getColumnDefinition(OptionalInt.empty(), Dialect.MYSQL) == "VECTOR"
        converter.getColumnDefinition(OptionalInt.empty(), Dialect.H2) == "VARCHAR(255)"
    }

    def "convertToEntityValue without converter throws"() {
        given:
        def converter = new TestDoubleVectorConverter([:]) // no dialect converter
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.ORACLE
        }

        when:
        converter.convertToEntityValue("ignored", ctx)

        then:
        thrown(io.micronaut.data.exceptions.DataAccessException)
    }

    def "convertToPersistedValue uses converter map for MYSQL dialect"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConvertor) {
            getPersistedType() >> persistedType
            getDialect() >> Dialect.MYSQL
            getName() >> "mysql-vector"
            // entity -> persisted
            convert(_, _) >> { Vector v, Class t ->
                "mysql:${v.toDoubleArray().join(',')}"
            }
            // persisted -> entity (not used in this test)
            convert(_, Class) >> { Object obj, Class target ->
                Vector.of(9d, 9d)
            }
        }
        def converter = new TestDoubleVectorConverter(["MYSQL": delegatingConverter])
        def ctx = Stub(DialectConversionContext) {
            getDialect() >> Dialect.MYSQL
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        def persisted = converter.convertToPersistedValue(v, ctx)

        then:
        persisted == "mysql:1.0,2.0"
    }
}
