package io.micronaut.data.jdbc.convert.vendor

import com.pgvector.PGsparsevec
import com.pgvector.PGvector
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.Vector
import org.postgresql.util.PGobject
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !io.micronaut.data.jdbc.convert.vendor.PostgresTypeConvertersFactorySpec.hasPg() })
class PostgresTypeConvertersFactorySpec extends Specification {

    static boolean hasPg() {
        try {
            Class.forName('com.pgvector.PGvector')
            return true
        } catch (Throwable t) {
            return false
        }
    }

    def "FloatVector is converted to PGvector"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        when:
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvPg = f.fromFloatVectorToPgVector().convert(fv, Object, null).get()

        then:
        fvPg.getClass().name == 'com.pgvector.PGvector'
        fvPg.toArray().toList() == [1f, 2f]
    }

    def "SparseFloatVector is converted to PGsparsevec"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def sparse = SparseFloatVector.fromDense([0f, 0f, 2.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 3f] as float[])

        when:
        def pg = f.fromSparseFloatVectorToPgSparsevec().convert(sparse, Object, null).get()

        then:
        pg.getClass().name == 'com.pgvector.PGsparsevec'
        pg.toArray().toList() == [0f, 0f, 2.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 3f]
    }

    def "PGvector read path converts back to vector subtypes"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        and: "PGvector instance"
        def pg = new PGvector([1f, 2.6f] as float[])

        expect:
        f.fromPgObjectToFloatVector().convert(pg, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and:
        f.fromPgObjectToVector().convert(pg, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and:
        f.fromPgObjectToSparseFloatVector().convert(pg, SparseFloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]
    }

    def "PGobject read path converts back to vector subtypes"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def pg = new PGobject(type: 'vector', value: '[1.0,2.6]')

        expect:
        f.fromPgObjectToFloatVector().convert(pg, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]
        f.fromPgObjectToVector().convert(pg, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]
        f.fromPgObjectToSparseFloatVector().convert(pg, SparseFloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]
        f.fromPgObjectToPgVector().convert(pg, PGvector, null).get().toArray().toList() == [1f, 2.6f]
    }

    def "PGsparsevec read path converts to dense and sparse targets"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def sparse = new PGsparsevec([1f, 0f, 2f, 0f] as float[])

        when:
        def asFloat = f.fromPgObjectToFloatVector().convert(sparse, FloatVector, null).get()
        def asVector = f.fromPgObjectToVector().convert(sparse, Vector, null).get()
        def asSparse = f.fromPgObjectToSparseFloatVector().convert(sparse, SparseFloatVector, null).get()

        then:
        asFloat.toFloatArray().toList() == [1f, 0f, 2f, 0f]
        asVector.toFloatArray().toList() == [1f, 0f, 2f, 0f]
        asSparse.toFloatArray().toList() == [1f, 0f, 2f, 0f]
    }

    def "JDBC sparse vector converter reads driver PGobject sparsevec values"() {
        given:
        def converter = new PostgresJdbcSparseVectorConverter(ConversionService.SHARED)
        def pg = new PGobject(type: 'sparsevec', value: '{1:1.5,4:-2}/5')

        when:
        def sparse = (SparseFloatVector) converter.convert(pg, SparseFloatVector)

        then:
        converter.persistedType == PGobject
        sparse.indices().toList() == [0, 3]
        sparse.values().toList() == [1.5f, -2f]
        sparse.toFloatArray().toList() == [1.5f, 0f, 0f, -2f, 0f]
    }
}
