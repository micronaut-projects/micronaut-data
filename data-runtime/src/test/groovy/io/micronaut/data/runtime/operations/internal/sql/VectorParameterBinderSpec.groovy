package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.data.model.DataType
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
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

    private static final class OracleTestVectorTypeConverter implements VectorTypeConverter<String> {

        @Override
        String convert(Vector vector) {
            return "oracle:${Arrays.toString(vector.toDoubleArray())}"
        }

        @Override
        Vector convert(String object, Class<Vector> targetType) {
            throw new UnsupportedOperationException("not needed")
        }

        @Override
        List<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector]
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
}
