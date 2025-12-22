package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.core.type.Argument
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext
import io.micronaut.data.model.vector.FloatVector
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
}
