package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport
import io.micronaut.data.model.vector.search.ScoringFunction
import spock.lang.Specification

class PostgresVectorScoringSpec extends Specification {

    def postgres = new VectorScoringDialectSupports.Postgres()

    void "Hamming and Jaccard distances are supported"() {
        expect:
        postgres.supportedScoringFunctions().containsAll([ScoringFunction.HAMMING, ScoringFunction.JACCARD])
    }

    void "a binary vector query defaults to Hamming distance"() {
        expect:
        postgres.defaultScoringFunction('SELECT * FROM doc ORDER BY embedding <~> ? LIMIT 2') == ScoringFunction.HAMMING
        postgres.defaultScoringFunction('SELECT * FROM doc ORDER BY embedding <=> ? LIMIT 2') == ScoringFunction.COSINE
        VectorScoringDialectSupport.defaultScoringFunctionForQuery(Dialect.POSTGRES, 'embedding <~> ?') == ScoringFunction.HAMMING
    }

    void "the selected scoring function replaces the default operator"() {
        expect:
        postgres.adaptQueryForScoringFunction(query, selected) == adapted

        where:
        query            | selected                     | adapted
        'e <~> ? <= ?'   | ScoringFunction.JACCARD      | 'e <%> ? <= ?'
        'e <~> ? <= ?'   | ScoringFunction.HAMMING      | 'e <~> ? <= ?'
        'e <=> ? <= ?'   | ScoringFunction.L2_EUCLIDEAN | 'e <-> ? <= ?'
    }
}
