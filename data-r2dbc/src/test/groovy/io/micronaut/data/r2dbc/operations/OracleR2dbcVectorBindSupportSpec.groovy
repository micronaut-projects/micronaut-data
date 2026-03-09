package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class OracleR2dbcVectorBindSupportSpec extends Specification {

    def "creates typed Oracle VECTOR parameter for dense primitives"() {
        when:
        def parameter = OracleR2dbcVectorBindSupport.toTypedVectorParameter([1d, 2d] as double[])

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value instanceof double[]
        parameter.value.toList() == [1d, 2d]
    }

    def "creates typed Oracle VECTOR parameter for dense string literal"() {
        when:
        def parameter = OracleR2dbcVectorBindSupport.toTypedVectorParameter('[1.0, 2.5, -3.0]')

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value instanceof double[]
        parameter.value.toList() == [1d, 2.5d, -3d]
    }

    def "does not force typed bind for sparse-like string literal"() {
        expect:
        OracleR2dbcVectorBindSupport.toTypedVectorParameter('[8, [1, 3], [0.5, 1.0]]') == null
    }

    def "converts dense string literal to sparse literal"() {
        expect:
        OracleR2dbcVectorBindSupport.toSparseVectorLiteral('[1.0, 0.0, -3.0]') == '[3,[0,2],[1.0,-3.0]]'
    }

    def "keeps sparse string literal as is"() {
        expect:
        OracleR2dbcVectorBindSupport.toSparseVectorLiteral('[3,[0,2],[1.0,-3.0]]') == '[3,[0,2],[1.0,-3.0]]'
    }

    def "converts dense INT8 string to sparse INT8 literal for sparse query"() {
        given:
        def query = 'SELECT VECTOR_DISTANCE(TO_VECTOR(col,5,INT8,SPARSE),TO_VECTOR(?,5,INT8,SPARSE),COSINE) FROM dual'

        expect:
        OracleR2dbcVectorBindSupport.toSparseVectorLiteral('[0, 10, 0, 20, 0]', query) == '[5,[1,3],[10,20]]'
    }

    def "detects sparse INT8 kind with mixed case and spacing"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 5, int8 , sparse), TO_VECTOR(?,5,int8,sparse), cosine) from dual'

        expect:
        OracleR2dbcVectorBindSupport.toSparseVectorLiteral('[0, 10, 0, 20, 0]', query) == '[5,[1,3],[10,20]]'
    }

    def "detects sparse FLOAT32 kind with mixed case and spacing"() {
        given:
        def query = 'select VECTOR_DISTANCE(TO_VECTOR(col, 3, float32 , sparse), TO_VECTOR(?,3,FLOAT32,SPARSE), cosine) from dual'

        expect:
        OracleR2dbcVectorBindSupport.toSparseVectorLiteral('[1.5, 0, 2.5]', query) == '[3,[0,2],[1.5,2.5]]'
    }

    def "creates typed Oracle VECTOR parameter from Vector value"() {
        when:
        def parameter = OracleR2dbcVectorBindSupport.toTypedVectorParameter(Vector.of(1d, 2d, 3d))

        then:
        parameter != null
        parameter.type.name == 'VECTOR'
        parameter.value instanceof double[]
        parameter.value.toList() == [1d, 2d, 3d]
    }
}
