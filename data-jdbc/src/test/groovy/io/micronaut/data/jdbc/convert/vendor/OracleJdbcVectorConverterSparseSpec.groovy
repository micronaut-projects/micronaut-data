package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.Vector
import oracle.jdbc.OracleType
import oracle.sql.VECTOR
import spock.lang.Specification

class OracleJdbcVectorConverterSparseSpec extends Specification {

    void "oracle jdbc converter uses typed VECTOR persisted values"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        def converter = ctx.getBean(OracleJdbcVectorConverter)
        FloatVector floatVector = Vector.of([1f, 2f, 3f] as float[])
        ByteVector byteVector = Vector.of([1, 2, 3] as byte[])

        when:
        VECTOR persistedFloat = converter.convert(floatVector)
        VECTOR persistedByte = converter.convert(byteVector)
        Vector roundTripFloat = converter.convert(persistedFloat, Vector)
        Vector roundTripByte = converter.convert(persistedByte, Vector)

        then:
        persistedFloat.getType() == OracleType.VECTOR_FLOAT32
        persistedByte.getType() == OracleType.VECTOR_INT8
        roundTripFloat.toFloatArray().toList() == [1f, 2f, 3f]
        roundTripByte.toByteArray().toList() == [1, 2, 3]

        cleanup:
        ctx.close()
    }

    void "oracle jdbc converter accepts SparseFloatVector and round-trips values"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        def converter = ctx.getBean(OracleJdbcVectorConverter)
        def sparse = new SparseFloatVector(5, [1, 3] as int[], [2.5f, 4f] as float[])

        when:
        VECTOR persisted = converter.convert(sparse)
        Vector roundTrip = converter.convert(persisted, Vector)

        then:
        persisted != null
        roundTrip.toFloatArray().toList() == [0f, 2.5f, 0f, 4f, 0f]

        cleanup:
        ctx.close()
    }

    void "oracle sparse text parsing is rejected without parser fallback"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        def conversionService = ctx.getBean(ConversionService)

        when:
        conversionService.convertRequired("[5,[2,4],[10,20]]", Vector)

        then:
        thrown(ConversionErrorException)

        cleanup:
        ctx.close()
    }

    void "without runtime converters dense String-to-Vector conversion is unavailable"() {
        given:
        def conversionService = ConversionService.SHARED

        when:
        conversionService.convertRequired("[1.0,2.0]", Vector)

        then:
        thrown(ConversionErrorException)
    }
}
