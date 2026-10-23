package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.core.type.Argument
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.DataAccessType
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.mapper.ResultReader
import spock.lang.Specification

class VectorConverterSelectionSpec extends Specification {

    def "convertToPersistedValue throws when no converter for database type"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([]) // empty converter list -> no converters registered
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        conv.convertToPersistedValue((FloatVector) Vector.of([1f, 2f] as float[]), ctx)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Vectors aren't supported for the database POSTGRES")
    }

    def "convertToEntityValue throws when no converter for database type"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        conv.convertToEntityValue(new Object(), ctx)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Vectors aren't supported for the database POSTGRES")
    }

    def "readFromResultSet throws when no converter for database type"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([])
        def conversionContext = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }
        def reader = Stub(ResultReader<Object, Object>)

        when:
        conv.readFromResultSet(conversionContext, reader, new Object(), "embedding")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Vectors aren't supported for the database POSTGRES")
    }

    def "supports returns true only for matching vector subtype"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([])

        expect:
        conv.supports(Argument.of(FloatVector))      // matches subtype
        !conv.supports(Argument.of(Vector))          // generic Vector is not assignable to FloatVector
        !conv.supports(Argument.of(String))          // unrelated type
    }

    def "converter construction fails fast when duplicate database converters are provided"() {
        when:
        new DefaultFloatVectorAttributeConverter([new OracleFloatConverter(), new OracleFloatConverterDuplicate()])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Multiple VectorTypeConverter beans registered for database ORACLE")
    }

    def "selects the converter of the operation's data access type when JDBC and R2DBC converters are registered"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([new OracleJdbcFloatConverter(), new OracleR2dbcFloatConverter()])
        def jdbc = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getDataAccessType() >> DataAccessType.JDBC
        }
        def r2dbc = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getDataAccessType() >> DataAccessType.R2DBC
        }
        def vector = (FloatVector) Vector.of([1f, 2f] as float[])

        expect:
        conv.convertToPersistedValue(vector, jdbc) == "jdbc"
        conv.convertToPersistedValue(vector, r2dbc) == "r2dbc"
    }

    def "a converter without a data access type serves both driver APIs"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([new OracleFloatConverter()])
        def vector = (FloatVector) Vector.of([1f, 2f] as float[])

        expect:
        conv.convertToPersistedValue(vector, context(DataAccessType.JDBC)) != null
        conv.convertToPersistedValue(vector, context(DataAccessType.R2DBC)) != null
        conv.convertToPersistedValue(vector, context(null)) != null
    }

    def "the error names the data access type when only another API has a converter"() {
        given:
        def conv = new DefaultFloatVectorAttributeConverter([new OracleJdbcFloatConverter()])

        when:
        conv.convertToPersistedValue((FloatVector) Vector.of([1f] as float[]), context(DataAccessType.R2DBC))

        then:
        IllegalArgumentException e = thrown()
        e.message == "Vectors aren't supported for the database ORACLE with R2DBC"
    }

    def "a context without a data access type uses the first registered converter of either API"() {
        expect:
        new DefaultFloatVectorAttributeConverter([new OracleJdbcFloatConverter(), new OracleR2dbcFloatConverter()])
            .convertToPersistedValue((FloatVector) Vector.of([1f] as float[]), context(null)) == "jdbc"
        new DefaultFloatVectorAttributeConverter([new OracleR2dbcFloatConverter(), new OracleJdbcFloatConverter()])
            .convertToPersistedValue((FloatVector) Vector.of([1f] as float[]), context(null)) == "r2dbc"
    }

    def "selects sparse converter for sparse vectors when both postgres converters are registered"() {
        given:
        def conv = new DefaultVectorAttributeConverter([new PostgresDenseConverter(), new PostgresSparseConverter()])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        def persisted = conv.convertToPersistedValue(SparseFloatVector.fromDense([0f, 1f, 0f, 2f] as float[]), ctx)

        then:
        persisted == "pg-sparse:[0.0, 1.0, 0.0, 2.0]"
    }

    def "selects dense converter for dense vectors when both postgres converters are registered"() {
        given:
        def conv = new DefaultVectorAttributeConverter([new PostgresDenseConverter(), new PostgresSparseConverter()])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        def persisted = conv.convertToPersistedValue((FloatVector) Vector.of([1f, 2f] as float[]), ctx)

        then:
        persisted == "pg-dense:[1.0, 2.0]"
    }

    private static class OracleFloatConverter implements VectorTypeConverter<Object> {
        @Override
        Object convert(Vector vector) {
            return vector.toFloatArray()
        }

        @Override
        Vector convert(Object object, Class<Vector> targetType) {
            return Vector.of((float[]) object)
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [FloatVector] as Set
        }

        @Override
        DatabaseType databaseType() {
            return DatabaseType.ORACLE
        }

        @Override
        Class<Object> getPersistedType() {
            return Object
        }
    }

    private static final class OracleFloatConverterDuplicate extends OracleFloatConverter {
    }

    private static final class OracleJdbcFloatConverter extends OracleFloatConverter {
        @Override
        Object convert(Vector vector) {
            return "jdbc"
        }

        @Override
        DataAccessType dataAccessType() {
            return DataAccessType.JDBC
        }
    }

    private static final class OracleR2dbcFloatConverter extends OracleFloatConverter {
        @Override
        Object convert(Vector vector) {
            return "r2dbc"
        }

        @Override
        DataAccessType dataAccessType() {
            return DataAccessType.R2DBC
        }
    }

    private static class PostgresDenseConverter implements VectorTypeConverter<String> {
        @Override
        String convert(Vector vector) {
            return "pg-dense:${vector.toFloatArray().toList()}"
        }

        @Override
        Vector convert(String object, Class<Vector> targetType) {
            return (FloatVector) Vector.of([1f] as float[])
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector, FloatVector] as Set
        }

        @Override
        DatabaseType databaseType() {
            return DatabaseType.POSTGRES
        }

        @Override
        Class<String> getPersistedType() {
            return String
        }
    }

    private static final class PostgresSparseConverter extends PostgresDenseConverter {
        @Override
        String convert(Vector vector) {
            return "pg-sparse:${vector.toFloatArray().toList()}"
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector, SparseFloatVector] as Set
        }

        @Override
        boolean isSparseSupported() {
            return true
        }
    }

    private DatabaseTypeConversionContext context(DataAccessType dataAccessType) {
        return Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getDataAccessType() >> dataAccessType
        }
    }
}
