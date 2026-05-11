package io.micronaut.data.model.schema.sql

import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider
import io.micronaut.data.model.schema.sql.metadata.VectorIndexMetadata
import spock.lang.Specification

class SqlIndexMappingSpec extends Specification {

    void "constructors populate optional fields"() {
        given:
        def provider = Stub(SqlIndexDefinitionProvider)

        when:
        def plain = new SqlIndexMapping('idx_plain', false, ['a'] as String[])
        def providerOnly = new SqlIndexMapping('idx_provider', true, ['b'] as String[], provider)
        def vector = new SqlIndexMapping('idx_vector', false, ['c'] as String[], provider,
            new VectorIndexMetadata(VectorIndexType.HNSW, VectorIndexType.DistanceType.COSINE, 90, true), false)

        then:
        plain.sqlIndexDefinitionProvider() == null
        plain.vectorIndexMetadata() == null
        providerOnly.sqlIndexDefinitionProvider().is(provider)
        providerOnly.vectorIndexMetadata() == null
        vector.vectorIndexMetadata().sparse()
    }

    void "equals and hashCode compare arrays by content"() {
        given:
        def metadata = new VectorIndexMetadata(VectorIndexType.HNSW, VectorIndexType.DistanceType.L2_EUCLIDEAN, 80, false)

        expect:
        new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata, false) ==
            new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata, false)
        new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata, false).hashCode() ==
            new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata, false).hashCode()
    }

    void "equals includes sqlIndexDefinitionProvider"() {
        given:
        def provider1 = Stub(SqlIndexDefinitionProvider)
        def provider2 = Stub(SqlIndexDefinitionProvider)

        expect:
        new SqlIndexMapping('idx', false, ['c1'] as String[], provider1, null, false) !=
            new SqlIndexMapping('idx', false, ['c1'] as String[], provider2, null, false)
    }

    void "toString contains key fields"() {
        expect:
        new SqlIndexMapping('idx_name', true, ['c1', 'c2'] as String[]).toString().contains("name='idx_name'")
    }
}
