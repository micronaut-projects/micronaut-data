package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.DataType
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class DefaultR2dbcRepositoryOperationsSpec extends Specification {

    void "oracle vector binding candidate is restricted to vector-like values"() {
        expect:
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.STRING, '[1,2,3]') == false
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.BYTE_ARRAY, [1, 2, 3] as byte[]) == false
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.BYTE_ARRAY, Vector.of([1f, 2f] as float[]))
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.OBJECT, Vector.of([1f, 2f] as float[]))
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.OBJECT, '[1,2,3]')
        DefaultR2dbcRepositoryOperations.isOracleVectorBindCandidate(DataType.OBJECT, 'hello') == false
    }
}
