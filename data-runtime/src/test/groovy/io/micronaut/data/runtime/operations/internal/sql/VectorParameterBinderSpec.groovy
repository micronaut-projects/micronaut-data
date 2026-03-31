package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.data.model.DataType
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

import java.util.Arrays

class VectorParameterBinderSpec extends Specification {

    void "oracle vector binder converts vector using dialect converter"() {
        given:
        def binder = VectorParameterBinder.create([new OracleTestVectorTypeConverter()])

        when:
        def prepared = binder.bind(Dialect.ORACLE, DataType.OBJECT, Vector.of([1d, 2d] as double[]))

        then:
        prepared.dataType() == DataType.OBJECT
        prepared.value() == "oracle:[1.0, 2.0]"
    }

    void "oracle vector binder does not touch JSON byte payloads"() {
        given:
        def binder = VectorParameterBinder.create([new OracleTestVectorTypeConverter()])
        byte[] payload = [1, 2, 3] as byte[]

        when:
        def prepared = binder.bind(Dialect.ORACLE, DataType.JSON, payload)

        then:
        prepared.dataType() == DataType.JSON
        prepared.value().is(payload)
    }

    void "unsupported dialect binder keeps vector value unchanged"() {
        given:
        def binder = VectorParameterBinder.create([new OracleTestVectorTypeConverter()])

        when:
        def prepared = binder.bind(Dialect.POSTGRES, DataType.OBJECT, Vector.of(1d, 2d))

        then:
        prepared.dataType() == DataType.OBJECT
        prepared.value() instanceof Vector
    }

    void "unsupported dialect binder ignores non-vector values"() {
        given:
        def binder = VectorParameterBinder.create([new OracleTestVectorTypeConverter()])

        when:
        def prepared = binder.bind(Dialect.POSTGRES, DataType.OBJECT, "value")

        then:
        prepared.dataType() == DataType.OBJECT
        prepared.value() == "value"
    }

    void "fails fast when multiple converters target same database"() {
        when:
        VectorParameterBinder.create([new OracleTestVectorTypeConverter(), new OracleDuplicateVectorTypeConverter()])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Multiple VectorTypeConverter beans registered for database ORACLE")
    }

    void "binder prefers sparse converter for sparse vectors"() {
        given:
        def binder = VectorParameterBinder.create([new PostgresDenseVectorTypeConverter(), new PostgresSparseVectorTypeConverter()])

        when:
        def prepared = binder.bind(Dialect.POSTGRES, DataType.OBJECT, SparseFloatVector.fromDense([0f, 1f, 0f, 2f] as float[]))

        then:
        prepared.dataType() == DataType.OBJECT
        prepared.value() == "pg-sparse:[0.0, 1.0, 0.0, 2.0]"
    }

    void "binder prefers dense converter for dense vectors"() {
        given:
        def binder = VectorParameterBinder.create([new PostgresDenseVectorTypeConverter(), new PostgresSparseVectorTypeConverter()])

        when:
        def prepared = binder.bind(Dialect.POSTGRES, DataType.OBJECT, Vector.of([1f, 2f] as float[]))

        then:
        prepared.dataType() == DataType.OBJECT
        prepared.value() == "pg-dense:[1.0, 2.0]"
    }

    private static class OracleTestVectorTypeConverter implements VectorTypeConverter<String> {

        @Override
        String convert(Vector vector) {
            return "oracle:${Arrays.toString(vector.toDoubleArray())}"
        }

        @Override
        Vector convert(String object, Class<Vector> targetType) {
            throw new UnsupportedOperationException("not needed")
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector] as Set
        }

        @Override
        DatabaseType databaseType() {
            return DatabaseType.ORACLE
        }

        @Override
        Class<String> getPersistedType() {
            return String
        }
    }

    private static final class OracleDuplicateVectorTypeConverter extends OracleTestVectorTypeConverter {
    }

    private static class PostgresDenseVectorTypeConverter implements VectorTypeConverter<String> {

        @Override
        String convert(Vector vector) {
            return "pg-dense:${Arrays.toString(vector.toFloatArray())}"
        }

        @Override
        Vector convert(String object, Class<Vector> targetType) {
            throw new UnsupportedOperationException("not needed")
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector] as Set
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

    private static final class PostgresSparseVectorTypeConverter extends PostgresDenseVectorTypeConverter {

        @Override
        String convert(Vector vector) {
            return "pg-sparse:${Arrays.toString(vector.toFloatArray())}"
        }

        @Override
        boolean isSparseSupported() {
            return true
        }
    }
}
