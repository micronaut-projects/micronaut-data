package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.SparseByteVector
import io.micronaut.core.convert.ConversionService
import oracle.jdbc.OracleType
import oracle.sql.VECTOR
import spock.lang.Specification

class OracleR2dbcVectorBindSupportSpec extends Specification {

    private final ConversionService conversionService = Mock(ConversionService)
    private final OracleR2dbcVectorBindSupport bindSupport = new OracleR2dbcVectorBindSupport(conversionService)

    def "creates typed Oracle VECTOR parameter for dense Vector"() {
        given:
        conversionService.convert(_ as Vector, VECTOR) >> Optional.of(VECTOR.ofFloat64Values([1d, 2d] as double[]))

        when:
        def parameter = bindSupport.toTypedVectorParameter(Vector.of(1d, 2d), null)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "returns null for null vector binding value"() {
        expect:
        bindSupport.toTypedVectorParameter(null, null) == null
    }

    def "creates typed Oracle VECTOR parameter for sparse INT8 value"() {
        given:
        def sparse = new SparseByteVector(5, [1, 3] as int[], [10, 20] as byte[])
        conversionService.convert(_ as Vector, VECTOR) >> Optional.of(VECTOR.ofInt8Values(VECTOR.SparseByteArray.of(5, [1, 3] as int[], [10, 20] as byte[])))

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, null)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
        ((VECTOR) parameter.value).type == OracleType.VECTOR_INT8
    }

    def "creates typed Oracle VECTOR parameter for sparse FLOAT32 value"() {
        given:
        def sparse = new SparseFloatVector(3, [0, 2] as int[], [1.5f, 2.5f] as float[])
        conversionService.convert(_ as Vector, VECTOR) >> Optional.of(VECTOR.ofFloat32Values(
            VECTOR.SparseFloatArray.of(3, [0, 2] as int[], [1.5f, 2.5f] as float[])
        ))

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, null)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
        ((VECTOR) parameter.value).type == OracleType.VECTOR_FLOAT32
    }

    def "query text does not affect native VECTOR conversion"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 3, float32, sparse), TO_VECTOR(?,3,FLOAT32,SPARSE), cosine) from dual'
        def sparse = new SparseFloatVector(3, [0, 2] as int[], [1.5f, 2.5f] as float[])
        conversionService.convert(_ as Vector, VECTOR) >> Optional.of(VECTOR.ofFloat32Values(
            VECTOR.SparseFloatArray.of(3, [0, 2] as int[], [1.5f, 2.5f] as float[])
        ))

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, query)

        then:
        parameter != null
        ((VECTOR) parameter.value).type == OracleType.VECTOR_FLOAT32
    }

    def "creates typed Oracle VECTOR parameter from Vector value"() {
        given:
        conversionService.convert(_ as Vector, VECTOR) >> Optional.of(VECTOR.ofFloat64Values([1d, 2d, 3d] as double[]))

        when:
        def parameter = bindSupport.toTypedVectorParameter(Vector.of(1d, 2d, 3d), null)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "fails fast when value is string"() {
        when:
        bindSupport.toTypedVectorParameter('not-a-vector', null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('String VECTOR literals are not supported')
    }

    def "returns null for unsupported raw primitive arrays"() {
        when:
        def parameter = bindSupport.toTypedVectorParameter([1d, 2d] as double[], null)

        then:
        parameter == null
    }

    def "fails fast when conversion service cannot convert vector"() {
        given:
        conversionService.convert(_ as Vector, VECTOR) >> Optional.empty()

        when:
        bindSupport.toTypedVectorParameter(Vector.of(1d, 2d), null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('Cannot convert')
    }
}
