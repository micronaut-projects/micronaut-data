package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.r2dbc.spi.Parameter
import spock.lang.Specification

class DefaultR2dbcRepositoryOperationsSpec extends Specification {

    void "dialect map selects correct vector bind support"() {
        given:
        def postgresSupport = new StubVectorBindSupport(Dialect.POSTGRES)
        def oracleSupport = new StubVectorBindSupport(Dialect.ORACLE)
        def supports = [postgresSupport, oracleSupport]
        def supportByDialect = supports.collectEntries { [(it.getDialect()): it] }

        expect:
        supportByDialect.get(Dialect.ORACLE).is(oracleSupport)
        supportByDialect.get(Dialect.POSTGRES).is(postgresSupport)
        supportByDialect.get(Dialect.MYSQL) == null
    }

    void "vector binding candidate accepts abstract Vector API payload"() {
        expect:
        DefaultR2dbcRepositoryOperations.isVectorBindingCandidate(Vector.of(1d, 2d))
        !DefaultR2dbcRepositoryOperations.isVectorBindingCandidate("[1.0,2.0]")
    }

    private static final class StubVectorBindSupport implements VectorBindSupport {

        private final Dialect dialect

        private StubVectorBindSupport(Dialect dialect) {
            this.dialect = dialect
        }

        @Override
        Dialect getDialect() {
            return dialect
        }

        @Override
        Parameter toTypedVectorParameter(Object value, String query) {
            return null
        }
    }
}
