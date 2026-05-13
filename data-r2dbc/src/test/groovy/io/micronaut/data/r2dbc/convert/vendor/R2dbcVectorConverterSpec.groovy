package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class R2dbcVectorConverterSpec extends Specification {

    def "postgres converter delegates supported vector conversion to conversion service"() {
        given:
        def conversionService = Mock(ConversionService)
        def converter = new PostgresR2dbcVectorConverter(conversionService)
        def vector = (FloatVector) Vector.of([1f, 2f] as float[])
        def persisted = io.r2dbc.postgresql.codec.Vector.of([1f, 2f] as float[])

        when:
        def converted = converter.convert(vector)

        then:
        1 * conversionService.convert(vector, io.r2dbc.postgresql.codec.Vector) >> Optional.of(persisted)
        converted.is(persisted)
    }

    def "postgres converter converts persisted codec value to requested vector type"() {
        given:
        def conversionService = Mock(ConversionService)
        def converter = new PostgresR2dbcVectorConverter(conversionService)
        def persisted = io.r2dbc.postgresql.codec.Vector.of([1f, 2f] as float[])
        def vector = (FloatVector) Vector.of([1f, 2f] as float[])

        when:
        def converted = converter.convert(persisted, FloatVector as Class<Vector>)

        then:
        1 * conversionService.convert(persisted, FloatVector) >> Optional.of(vector)
        converted.is(vector)
    }

    def "postgres converter reports conversion service misses"() {
        given:
        def conversionService = Mock(ConversionService)
        def converter = new PostgresR2dbcVectorConverter(conversionService)
        def vector = (FloatVector) Vector.of([1f] as float[])

        when:
        converter.convert(vector)

        then:
        1 * conversionService.convert(vector, io.r2dbc.postgresql.codec.Vector) >> Optional.empty()
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('Conversion service cannot convert')
    }

    def "postgres converter rejects unsupported target vector types"() {
        given:
        def conversionService = Mock(ConversionService)
        def converter = new PostgresR2dbcVectorConverter(conversionService)
        def persisted = io.r2dbc.postgresql.codec.Vector.of([1f] as float[])

        when:
        converter.convert(persisted, DoubleVector as Class<Vector>)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('POSTGRES does not support')
        0 * conversionService._
    }
}
