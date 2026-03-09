package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class VectorParameterBinderSpec extends Specification {

    void "oracle vector binder converts OBJECT vector values to string"() {
        given:
        def binder = VectorParameterBinder.forDialect(Dialect.ORACLE)

        when:
        def prepared = binder.bind(DataType.OBJECT, Vector.of([1d, 2d] as double[]))

        then:
        prepared.dataType() == DataType.STRING
        prepared.value() == "[1.0, 2.0]"
    }

    void "oracle vector binder does not touch JSON byte payloads"() {
        given:
        def binder = VectorParameterBinder.forDialect(Dialect.ORACLE)
        byte[] payload = [1, 2, 3] as byte[]

        when:
        def prepared = binder.bind(DataType.JSON, payload)

        then:
        prepared.dataType() == DataType.JSON
        prepared.value().is(payload)
    }
}
