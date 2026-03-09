package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import io.micronaut.data.runtime.mapper.ResultReader
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

/**
 * Verifies transform/conversion behavior of AbstractVectorAttributeConverter:
 * - Delegation to a VectorTypeConverter when a converter is registered for a DatabaseType
 * - Throws when no converter is registered for a DatabaseType
 * - readFromResultSet delegates to ResultReader with persisted type
 * - convertToEntityValue delegates through VectorTypeConverter
 * - Column definition rendering per database type
 */
class VectorAttributeConverterTransformSpec extends Specification {

    /**
     * Minimal concrete converter for testing that extends the package-private
     * AbstractVectorAttributeConverter from the same package.
     */
    static class TestDoubleVectorConverter extends AbstractVectorAttributeConverter<DoubleVector, Object> {
        TestDoubleVectorConverter(List<VectorTypeConverter<?>> converterList) {
            super(converterList, DoubleVector.class)
        }

        @Override
        String getOracleType() {
            return "FLOAT64"
        }
    }

    def "convertToPersistedValue uses converter map for POSTGRES database"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> persistedType
            databaseType() >> DatabaseType.POSTGRES
            // entity -> persisted
            convert(_ as Vector) >> { Vector v ->
                "pg:${v.toDoubleArray().join(',')}"
            }
            // persisted -> entity (not used in this test)
            convert(_ as String, _ as Class) >> { Object obj, Class target ->
                Vector.of(9d, 9d)
            }
        }
        def converter = new TestDoubleVectorConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        def persisted = converter.convertToPersistedValue(v, ctx)

        then:
        persisted == "pg:1.0,2.0"
    }

    def "convertToPersistedValue throws for ORACLE when no converter exists"() {
        given:
        def converter = new TestDoubleVectorConverter([]) // no converter in the list
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        converter.convertToPersistedValue(v, ctx)

        then:
        thrown(IllegalArgumentException)
    }

    def "readFromResultSet delegates to ResultReader with persisted type for POSTGRES"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> persistedType
            databaseType() >> DatabaseType.POSTGRES
            convert(_ as Vector) >> { Vector v -> "pg:${v.toDoubleArray().join(',')}" }
            convert(_ as String, _ as Class) >> { Object obj, Class target -> Vector.of(1d, 2d) }
        }
        def converter = new TestDoubleVectorConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
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
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.POSTGRES
            // entity -> persisted (not used here)
            convert(_ as Vector) >> { Vector v -> "pg:${v.toDoubleArray().join(',')}" }
            // persisted -> entity path
            convert(_ as String, _ as Class) >> { String obj, Class target ->
                Vector.of(7d, 8d)
            }
        }
        def converter = new TestDoubleVectorConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        def entity = converter.convertToEntityValue("ignored-persisted", ctx)

        then:
        entity instanceof DoubleVector
        entity.toDoubleArray().toList() == [7d, 8d]
    }

    def "getColumnDefinition renders per database type"() {
        given:
        def converter = new TestDoubleVectorConverter([])
        def arg = io.micronaut.core.type.Argument.of(DoubleVector)

        expect:
        converter.getColumnDefinition(arg, DatabaseType.POSTGRES) == "vector"
        converter.getColumnDefinition(arg, DatabaseType.ORACLE) == "VECTOR(*,FLOAT64)"
        converter.getColumnDefinition(arg, DatabaseType.MYSQL) == "VECTOR"
        converter.getColumnDefinition(arg, DatabaseType.H2) == "VARCHAR(255)"
    }

    def "convertToEntityValue without converter throws"() {
        given:
        def converter = new TestDoubleVectorConverter([]) // no dialect converter
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
        }

        when:
        converter.convertToEntityValue("ignored", ctx)

        then:
        thrown(IllegalArgumentException)
    }

    def "convertToPersistedValue uses converter map for MYSQL database"() {
        given:
        def persistedType = String
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> persistedType
            databaseType() >> DatabaseType.MYSQL
            // entity -> persisted
            convert(_ as Vector) >> { Vector v ->
                "mysql:${v.toDoubleArray().join(',')}"
            }
            // persisted -> entity (not used in this test)
            convert(_ as String, _ as Class) >> { Object obj, Class target ->
                Vector.of(9d, 9d)
            }
        }
        def converter = new TestDoubleVectorConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.MYSQL
        }
        def v = (DoubleVector) Vector.of(1d, 2d)

        when:
        def persisted = converter.convertToPersistedValue(v, ctx)

        then:
        persisted == "mysql:1.0,2.0"
    }

    def "generic Vector sparse values are coerced to FloatVector for ORACLE"() {
        given:
        def vectorConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.ORACLE
            convert(_ as Vector) >> { Vector v ->
                assert v instanceof FloatVector
                "oracle:${v.class.simpleName}"
            }
        }
        def converter = new DefaultVectorAttributeConverter([vectorConverter])
        def metadata = new DefaultAnnotationMetadata(
                ["io.micronaut.data.annotation.VectorStorage": ["sparse": true]],
                Collections.emptyMap(),
                Collections.emptyMap(),
                ["io.micronaut.data.annotation.VectorStorage": ["sparse": true]],
                Collections.emptyMap()
        )
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getAnnotationMetadata() >> metadata
        }

        when:
        def persisted = converter.convertToPersistedValue(Vector.of(1d, 0d, 0d), ctx)

        then:
        persisted == "oracle:FloatVector"
    }
}
