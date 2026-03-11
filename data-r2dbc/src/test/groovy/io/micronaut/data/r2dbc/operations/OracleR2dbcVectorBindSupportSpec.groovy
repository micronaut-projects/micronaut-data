package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.SparseByteVector
import spock.lang.Specification

class OracleR2dbcVectorBindSupportSpec extends Specification {

    private final OracleR2dbcVectorBindSupport bindSupport = new OracleR2dbcVectorBindSupport()

    def "creates typed Oracle VECTOR parameter for dense Vector"() {
        when:
        def parameter = bindSupport.toTypedVectorParameter(Vector.of(1d, 2d))

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "creates typed Oracle VECTOR parameter for sparse INT8 query"() {
        given:
        def query = 'SELECT VECTOR_DISTANCE(TO_VECTOR(col,5,INT8,SPARSE),TO_VECTOR(?,5,INT8,SPARSE),COSINE) FROM dual'
        def sparse = new SparseByteVector(5, [1, 3] as int[], [10, 20] as byte[])

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, query)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "creates typed Oracle VECTOR parameter for sparse FLOAT32 query"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 3, float32 , sparse), TO_VECTOR(?,3,FLOAT32,SPARSE), cosine) from dual'
        def sparse = new SparseFloatVector(3, [0, 2] as int[], [1.5f, 2.5f] as float[])

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, query)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "detects sparse INT8 kind with mixed case and spacing"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 5, int8 , sparse), TO_VECTOR(?,5,int8,sparse), cosine) from dual'
        def sparse = new SparseByteVector(5, [1, 3] as int[], [10, 20] as byte[])

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, query)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "detects sparse FLOAT32 kind with mixed case and spacing"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 3, float32 , sparse), TO_VECTOR(?,3,FLOAT32,SPARSE), cosine) from dual'
        def sparse = new SparseFloatVector(3, [0, 2] as int[], [1.5f, 2.5f] as float[])

        when:
        def parameter = bindSupport.toTypedVectorParameter(sparse, query)

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "creates typed Oracle VECTOR parameter from Vector value"() {
        when:
        def parameter = bindSupport.toTypedVectorParameter(Vector.of(1d, 2d, 3d))

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value.class.name == 'oracle.sql.VECTOR'
    }

    def "fails fast when value is string"() {
        when:
        bindSupport.toTypedVectorParameter('not-a-vector')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('String VECTOR literals are not supported')
    }

    def "returns null for unsupported raw primitive arrays"() {
        when:
        def parameter = bindSupport.toTypedVectorParameter([1d, 2d] as double[])

        then:
        parameter == null
    }
}
